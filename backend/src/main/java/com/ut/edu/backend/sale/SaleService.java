package com.ut.edu.backend.sale;

import com.ut.edu.backend.common.SequentialCodeGenerator;
import com.ut.edu.backend.coupon.Coupon;
import com.ut.edu.backend.coupon.CouponRepository;
import com.ut.edu.backend.order.Order;
import com.ut.edu.backend.order.OrderItem;
import com.ut.edu.backend.order.OrderRepository;
import com.ut.edu.backend.order.OrderStatus;
import com.ut.edu.backend.order.SalesChannel;
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
    /** "Mã đặt hàng" for the order a delivery sale raises - KiotViet's own prefix for the document. */
    private static final String ORDER_CODE_PREFIX = "DH";
    private static final int MAX_CODE_RETRIES = 5;

    /** "Điểm" redemption rate - 1 point is worth 1,000 VND off the invoice. */
    private static final BigDecimal POINT_REDEMPTION_VALUE = BigDecimal.valueOf(1_000);
    /**
     * "Tích điểm" default earn rate - 1 point per 10,000 VND of loyalty-eligible
     * line total (Product#loyaltyPointsEnabled). A product with Product#loyaltyPoints
     * set earns that flat amount per unit instead, see the checkout loop below.
     */
    private static final BigDecimal POINT_EARN_RATE = BigDecimal.valueOf(10_000);

    private final SaleRepository saleRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final OrderRepository orderRepository;
    private final TenantGuard tenantGuard;

    /**
     * What one checkout produced. The invoice is always there; the order only
     * when the register was on "Bán giao hàng", and the caller needs it -
     * booking the parcel is a second call, and it has to say which order the
     * parcel is carrying.
     */
    public record CheckoutResult(Sale sale, Order order) {
    }

    @Transactional
    public CheckoutResult checkout(Long storeId, User cashier, CreateSaleRequest request) {
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
        int flatLoyaltyPointsEarned = 0;
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
                if (product.getLoyaltyPoints() != null) {
                    flatLoyaltyPointsEarned += product.getLoyaltyPoints() * itemReq.quantity();
                } else {
                    loyaltyEligibleSubtotal = loyaltyEligibleSubtotal.add(lineTotal);
                }
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
            int pointsEarned = loyaltyEligibleSubtotal.divide(POINT_EARN_RATE, 0, RoundingMode.DOWN).intValue() + flatLoyaltyPointsEarned;
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
                Order order = request.delivery() == null
                        ? null
                        : createDeliveryOrder(storeId, cashier, saved, request.delivery());
                return new CheckoutResult(saved, order);
            } catch (DataIntegrityViolationException e) {
                lastError = e;
            }
        }
        throw lastError;
    }

    /**
     * "Bán giao hàng" also writes an Order, so the sale shows up under "Đặt
     * hàng" where the shop looks for what it still has to send.
     *
     * The two documents are the same transaction seen from two sides and stay
     * that way: the Order carries no money of its own, it points at the Sale
     * (Order#sale) and the list reads what was collected from there. Nothing
     * here touches stock - checkout already decremented it above, and doing it
     * twice would sell the same unit to the same customer.
     */
    private Order createDeliveryOrder(Long storeId, User cashier, Sale sale, SaleDeliveryRequest delivery) {
        Order order = Order.builder()
                .store(tenantGuard.currentStoreRef())
                .customer(sale.getCustomer())
                .sale(sale)
                .createdBy(cashier)
                .salesChannel(SalesChannel.POS_DELIVERY)
                // COD means the courier still has to collect: the invoice
                // records the tender, but the shop has not been paid until the
                // parcel lands (the DELIVERED transition is what settles it).
                .status(delivery.codEnabled() ? OrderStatus.PENDING_COD : OrderStatus.PAID)
                .subtotal(sale.getSubtotal())
                .discountAmount(sale.getDiscountAmount()
                        .add(sale.getCouponDiscountAmount())
                        .add(sale.getPointsRedeemedAmount()))
                .otherCollectionAmount(sale.getOtherCollectionAmount())
                .shippingCost(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .total(sale.getTotalAmount())
                .couponCode(sale.getCouponCode())
                .recipientName(delivery.recipientName().trim())
                .shippingAddressLine1(delivery.address().trim())
                .shippingCity(blankToNull(delivery.provinceName()))
                .shippingStateProvince(blankToNull(delivery.districtName()))
                .shippingWard(blankToNull(delivery.wardName()))
                .shippingPhoneNumber(delivery.recipientPhone().trim())
                .shippingEmail(sale.getCustomer() != null ? sale.getCustomer().getEmail() : null)
                .shippingCarrier(blankToNull(delivery.carrierName()))
                .expectedDeliveryAt(delivery.expectedDeliveryAt())
                .notes(blankToNull(delivery.note()))
                .build();

        for (SaleItem item : sale.getItems()) {
            order.addItem(OrderItem.builder()
                    .product(item.getProduct())
                    .productName(item.getProductName())
                    .productSku(item.getProductSku())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .discountAmount(item.getDiscountAmount())
                    .subtotal(item.getLineTotal())
                    .build());
        }

        // Same retry-on-collision as the invoice code above: "Mã đặt hàng" is
        // a per-store sequence, not a DB sequence.
        DataIntegrityViolationException lastError = null;
        for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
            order.setOrderNumber(SequentialCodeGenerator.generate(
                    ORDER_CODE_PREFIX, orderRepository.countByStoreIdAndOrderNumberPrefix(storeId, ORDER_CODE_PREFIX) + attempt));
            try {
                Order savedOrder = orderRepository.save(order);
                log.info("Delivery order {} created for sale {}", savedOrder.getOrderNumber(), sale.getCode());
                return savedOrder;
            } catch (DataIntegrityViolationException e) {
                lastError = e;
            }
        }
        throw lastError;
    }

    /** An empty box on the delivery form means "not given", not an empty string on the label. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
