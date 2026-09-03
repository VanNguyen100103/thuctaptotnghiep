package com.ut.edu.backend.sale;

import com.ut.edu.backend.common.SequentialCodeGenerator;
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

    private final SaleRepository saleRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
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

            product.decrementStock(itemReq.quantity());
            product.incrementSoldCount(itemReq.quantity());
            productRepository.save(product);
        }
        sale.setSubtotal(subtotal);

        BigDecimal totalAmount = subtotal.subtract(sale.getDiscountAmount()).add(sale.getOtherCollectionAmount());
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
