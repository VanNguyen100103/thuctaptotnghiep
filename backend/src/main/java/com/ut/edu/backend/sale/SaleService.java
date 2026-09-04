package com.ut.edu.backend.sale;

import com.ut.edu.backend.common.SequentialCodeGenerator;
import com.ut.edu.backend.coupon.Coupon;
import com.ut.edu.backend.coupon.CouponRepository;
import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.product.ProductRepository;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.user.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * "Bán hàng" checkout - the POS register logic. Unlike PurchaseOrderService
 * there is no draft/complete split: {@link #checkout} both persists the sale
 * and applies its stock decrement in a single transaction, since a Sale only
 * ever exists once payment has been confirmed at the register.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaleService {

    private static final String CODE_PREFIX = "HD";
    private static final int MAX_CODE_RETRIES = 5;

    /** "Điểm" redemption rate - 1 point is worth 1,000 VND off the invoice. */
    private static final BigDecimal POINT_REDEMPTION_VALUE = BigDecimal.valueOf(1_000);
    /** "Tích điểm" earn rate - 1 point per 10,000 VND of loyalty-eligible line total (Product#loyaltyPointsEnabled). */
    private static final BigDecimal POINT_EARN_RATE = BigDecimal.valueOf(10_000);

    private final SaleRepository saleRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final TenantGuard tenantGuard;

    @Transactional
    public Sale checkout(Long storeId, User cashier, CreateSaleRequest request) {
        Customer customer = null;
        if (request.customerId() != null) {
            customer = customerRepository.findById(request.customerId())
                    .filter(c -> tenantGuard.isCurrentStore(c.getStore()))
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + request.customerId()));
        }

        Sale sale = Sale.builder()
                .store(tenantGuard.currentStoreRef())
                .customer(customer)
                .discountAmount(nz(request.discountAmount()))
                .otherCollectionAmount(nz(request.otherCollectionAmount()))
                .note(request.note())
                .createdBy(cashier)
                .build();

        // Line items: lock each product row so two registers selling the
        // last unit of the same product at once cannot both succeed (same
        // pessimistic-read pattern as PurchaseOrderService#complete).
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal loyaltyEligibleSubtotal = BigDecimal.ZERO;
        for (SaleItemRequest itemReq : request.items()) {
            Product product = productRepository.findByIdWithLock(itemReq.productId())
                    .filter(p -> tenantGuard.isCurrentStore(p.getStore()))
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + itemReq.productId()));
            if (product.getStockQuantity() < itemReq.quantity()) {
                throw new IllegalArgumentException(
                        "Sản phẩm \"" + product.getName() + "\" không đủ tồn kho (còn " + product.getStockQuantity() + ")");
            }
            BigDecimal unitPrice = itemReq.unitPrice() != null ? itemReq.unitPrice() : product.getPrice();
            BigDecimal discount = nz(itemReq.discountAmount());
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.quantity())).subtract(discount);

            sale.addItem(SaleItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .quantity(itemReq.quantity())
                    .unitPrice(unitPrice)
                    .discountAmount(discount)
                    .lineTotal(lineTotal)
                    .build());
            subtotal = subtotal.add(lineTotal);
            if (Boolean.TRUE.equals(product.getLoyaltyPointsEnabled())) {
                loyaltyEligibleSubtotal = loyaltyEligibleSubtotal.add(lineTotal);
            }

            product.decrementStock(itemReq.quantity());
            product.incrementSoldCount(itemReq.quantity());
            productRepository.save(product);
        }
        sale.setSubtotal(subtotal);

        // "Mã coupon" - re-validated and re-priced server-side, the client's
        // own /coupons/validate call (used for the live preview) is never
        // trusted for the actual charge.
        BigDecimal couponDiscount = BigDecimal.ZERO;
        String couponCode = request.couponCode() != null ? request.couponCode().trim() : null;
        if (couponCode != null && !couponCode.isBlank()) {
            Coupon coupon = couponRepository.findByCodeAndActiveTrue(couponCode)
                    .filter(c -> tenantGuard.isCurrentStore(c.getStore()))
                    .orElseThrow(() -> new IllegalArgumentException("Mã coupon không hợp lệ"));
            if (!coupon.isValid()) {
                throw new IllegalArgumentException("Mã coupon đã hết hạn hoặc hết lượt sử dụng");
            }
            if (coupon.getMinimumOrderValue() != null && subtotal.compareTo(coupon.getMinimumOrderValue()) < 0) {
                throw new IllegalArgumentException("Đơn hàng chưa đạt giá trị tối thiểu để áp dụng mã \"" + coupon.getCode() + "\"");
            }
            couponDiscount = coupon.calculateDiscount(subtotal);
            coupon.incrementUsedCount();
            couponRepository.save(coupon);
            sale.setCouponCode(coupon.getCode());
            sale.setCouponDiscountAmount(couponDiscount);
        }

        // "Điểm" - redeem against this sale (1 point = 1,000 VND), capped so
        // it can never take the invoice below zero.
        BigDecimal payableBeforePoints = subtotal.subtract(sale.getDiscountAmount()).subtract(couponDiscount).max(BigDecimal.ZERO);
        int pointsToRedeem = request.pointsToRedeem() != null ? request.pointsToRedeem() : 0;
        BigDecimal pointsRedeemedAmount = BigDecimal.ZERO;
        if (pointsToRedeem > 0) {
            if (customer == null) {
                throw new IllegalArgumentException("Cần chọn khách hàng để sử dụng điểm");
            }
            if (pointsToRedeem > customer.getLoyaltyPoints()) {
                throw new IllegalArgumentException("Khách hàng không đủ điểm (còn " + customer.getLoyaltyPoints() + " điểm)");
            }
            pointsRedeemedAmount = POINT_REDEMPTION_VALUE.multiply(BigDecimal.valueOf(pointsToRedeem)).min(payableBeforePoints);
            customer.setLoyaltyPoints(customer.getLoyaltyPoints() - pointsToRedeem);
            sale.setPointsRedeemed(pointsToRedeem);
            sale.setPointsRedeemedAmount(pointsRedeemedAmount);
        }

        // "Tích điểm" - earned from this sale's loyalty-eligible lines, credited on top of any redemption above.
        if (customer != null) {
            int pointsEarned = loyaltyEligibleSubtotal.divide(POINT_EARN_RATE, 0, RoundingMode.DOWN).intValue();
            customer.setLoyaltyPoints(customer.getLoyaltyPoints() + pointsEarned);
            sale.setPointsEarned(pointsEarned);
            customerRepository.save(customer);
        }

        BigDecimal totalAmount = subtotal
                .subtract(sale.getDiscountAmount())
                .subtract(couponDiscount)
                .subtract(pointsRedeemedAmount)
                .add(sale.getOtherCollectionAmount())
                .max(BigDecimal.ZERO);
        sale.setTotalAmount(totalAmount);

        // Payment lines: "Thanh toán nhiều phương thức" - any number of
        // tenders, must add up to at least what's owed. Overpaying in cash
        // is allowed (SaleResponse#changeAmount surfaces it as "tiền thừa").
        BigDecimal amountReceived = BigDecimal.ZERO;
        for (SalePaymentRequest paymentReq : request.payments()) {
            sale.addPayment(SalePayment.builder().method(paymentReq.method()).amount(paymentReq.amount()).build());
            amountReceived = amountReceived.add(paymentReq.amount());
        }
        sale.setAmountReceived(amountReceived);

        if (amountReceived.compareTo(totalAmount) < 0) {
            throw new IllegalArgumentException(
                    "Số tiền thanh toán chưa đủ, còn thiếu " + totalAmount.subtract(amountReceived));
        }

        // Retry on the rare race where two requests generate the same
        // next-in-sequence code concurrently (same pattern as PurchaseOrderService).
        DataIntegrityViolationException lastError = null;
        for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
            sale.setCode(SequentialCodeGenerator.generate(CODE_PREFIX, saleRepository.countByStoreId(storeId) + attempt));
            try {
                Sale saved = saleRepository.save(sale);
                log.info("Sale {} completed: {} line(s), total {}", saved.getCode(), saved.getItems().size(), saved.getTotalAmount());
                return saved;
            } catch (DataIntegrityViolationException e) {
                lastError = e;
            }
        }
        throw lastError;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
