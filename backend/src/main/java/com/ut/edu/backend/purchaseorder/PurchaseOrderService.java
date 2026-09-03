package com.ut.edu.backend.purchaseorder;

import com.ut.edu.backend.common.SequentialCodeGenerator;
import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.product.ProductRepository;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.supplier.Supplier;
import com.ut.edu.backend.supplier.SupplierRepository;
import com.ut.edu.backend.user.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * "Nhập hàng" business logic: draft save/edit, totals calculation and the
 * DRAFT -&gt; COMPLETED/CANCELLED transitions. Split out of the controller
 * (unlike AdminProductController's fatter style) because the calculation +
 * stock-mutation rules here are worth unit testing in isolation - see
 * PurchaseOrderServiceTest, and StoreStaffService for the same reasoning
 * applied elsewhere in this codebase.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderService {

    private static final String CODE_PREFIX = "PN";
    private static final int MAX_CODE_RETRIES = 5;

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final TenantGuard tenantGuard;

    @Transactional
    public PurchaseOrder create(Long storeId, User createdBy, SavePurchaseOrderRequest request) {
        PurchaseOrder po = PurchaseOrder.builder()
                .store(tenantGuard.currentStoreRef())
                .status(PurchaseOrderStatus.DRAFT)
                .createdBy(createdBy)
                .build();
        applyHeaderAndItems(po, request);

        // Retry on the rare race where two requests generate the same
        // next-in-sequence code concurrently (same pattern as SupplierController).
        DataIntegrityViolationException lastError = null;
        for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
            po.setCode(SequentialCodeGenerator.generate(CODE_PREFIX, purchaseOrderRepository.countByStoreId(storeId) + attempt));
            try {
                return purchaseOrderRepository.save(po);
            } catch (DataIntegrityViolationException e) {
                lastError = e;
            }
        }
        throw lastError;
    }

    /** "Lưu tạm" on an existing draft - only DRAFT purchase orders can still be edited. */
    @Transactional
    public PurchaseOrder update(PurchaseOrder existing, SavePurchaseOrderRequest request) {
        requireDraft(existing, "sửa");
        existing.clearItems();
        applyHeaderAndItems(existing, request);
        return purchaseOrderRepository.save(existing);
    }

    private void applyHeaderAndItems(PurchaseOrder po, SavePurchaseOrderRequest request) {
        Supplier supplier = null;
        if (request.supplierId() != null) {
            supplier = supplierRepository.findById(request.supplierId())
                    .filter(s -> tenantGuard.isCurrentStore(s.getStore()))
                    .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + request.supplierId()));
        }
        po.setSupplier(supplier);
        po.setDiscountAmount(nz(request.discountAmount()));
        po.setSupplierChargeAmount(nz(request.supplierChargeAmount()));
        po.setAmountPaid(nz(request.amountPaid()));
        po.setOtherCosts(nz(request.otherCosts()));
        po.setNote(request.note());

        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseOrderItemRequest> itemRequests = request.items() != null ? request.items() : List.of();
        for (PurchaseOrderItemRequest itemReq : itemRequests) {
            Product product = productRepository.findById(itemReq.productId())
                    .filter(p -> tenantGuard.isCurrentStore(p.getStore()))
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + itemReq.productId()));
            BigDecimal discount = nz(itemReq.discountAmount());
            BigDecimal lineTotal = itemReq.unitPrice()
                    .multiply(BigDecimal.valueOf(itemReq.quantity()))
                    .subtract(discount);
            po.addItem(PurchaseOrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .quantity(itemReq.quantity())
                    .unitPrice(itemReq.unitPrice())
                    .discountAmount(discount)
                    .lineTotal(lineTotal)
                    .build());
            total = total.add(lineTotal);
        }
        po.setTotalGoodsValue(total);
        // "Cần trả nhà cung cấp" = totalGoodsValue - discountAmount + supplierChargeAmount.
        // Deliberately does NOT subtract amountPaid (today's payment already
        // made is a separate line, "Tính vào công nợ" - see PurchaseOrderResponse)
        // nor otherCosts (paid to a 3rd party, e.g. a shipper, not the supplier).
        po.setPayableAmount(total.subtract(po.getDiscountAmount()).add(po.getSupplierChargeAmount()));
    }

    /**
     * "Hoàn thành" - locks the document and applies it to stock. Uses the
     * same pessimistic-lock read as checkout's stock decrements to avoid a
     * lost update if two receipts for the same product complete at once.
     * Also refreshes Product#costPrice to this receipt's unit price
     * (last-cost basis, not a weighted moving average - the simplest
     * defensible choice for this scope) so "Giá vốn" on Hàng hóa reflects
     * the latest purchase.
     */
    @Transactional
    public PurchaseOrder complete(PurchaseOrder po, User completedBy) {
        requireDraft(po, "hoàn thành");
        if (po.getItems().isEmpty()) {
            throw new IllegalArgumentException("Phiếu nhập chưa có hàng hóa nào");
        }
        for (PurchaseOrderItem item : po.getItems()) {
            Product product = productRepository.findByIdWithLock(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + item.getProduct().getId()));
            product.incrementStock(item.getQuantity());
            product.setCostPrice(item.getUnitPrice());
            productRepository.save(product);
        }
        po.setStatus(PurchaseOrderStatus.COMPLETED);
        po.setCompletedBy(completedBy);
        po.setCompletedAt(LocalDateTime.now());
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        log.info("Purchase order {} completed: {} line(s), stock incremented", saved.getCode(), saved.getItems().size());
        return saved;
    }

    /** "Hủy" - abandons a draft without ever touching stock. */
    @Transactional
    public PurchaseOrder cancel(PurchaseOrder po) {
        requireDraft(po, "hủy");
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        return purchaseOrderRepository.save(po);
    }

    private void requireDraft(PurchaseOrder po, String action) {
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new IllegalStateException("Chỉ có thể " + action + " phiếu nhập ở trạng thái Phiếu tạm");
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
