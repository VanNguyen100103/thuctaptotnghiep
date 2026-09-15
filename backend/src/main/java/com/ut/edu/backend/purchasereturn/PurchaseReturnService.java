package com.ut.edu.backend.purchasereturn;

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
 * "Trả hàng nhập" business logic - the mirror of PurchaseOrderService, and
 * split out of its controller for the same reason: the totals and the
 * stock transition are the parts worth testing on their own.
 *
 * Two deliberate differences from the receipt side. Completing a return
 * takes stock OUT, so it can fail on insufficient stock the way a POS sale
 * can (SaleService) - a shop cannot send back goods it no longer holds. And
 * a line whose product has since been deleted blocks completion rather than
 * being skipped: a receipt skipping such a line only foregoes stock it was
 * about to gain, while a return skipping one would hand goods back and take
 * nothing off the shelf.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseReturnService {

    private static final String CODE_PREFIX = "THN";
    private static final int MAX_CODE_RETRIES = 5;

    private final PurchaseReturnRepository purchaseReturnRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final TenantGuard tenantGuard;

    @Transactional
    public PurchaseReturn create(Long storeId, User createdBy, SavePurchaseReturnRequest request) {
        PurchaseReturn pr = PurchaseReturn.builder()
                .store(tenantGuard.currentStoreRef())
                .status(PurchaseReturnStatus.DRAFT)
                .createdBy(createdBy)
                .build();
        applyHeaderAndItems(pr, request);

        // Retry on the rare race where two requests generate the same
        // next-in-sequence code concurrently (same pattern as PurchaseOrderService).
        DataIntegrityViolationException lastError = null;
        for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
            pr.setCode(SequentialCodeGenerator.generate(CODE_PREFIX, purchaseReturnRepository.countByStoreId(storeId) + attempt));
            try {
                return purchaseReturnRepository.save(pr);
            } catch (DataIntegrityViolationException e) {
                lastError = e;
            }
        }
        throw lastError;
    }

    /** "Lưu tạm" on an existing draft - only DRAFT returns can still be edited. */
    @Transactional
    public PurchaseReturn update(PurchaseReturn existing, SavePurchaseReturnRequest request) {
        requireDraft(existing, "sửa");
        existing.clearItems();
        applyHeaderAndItems(existing, request);
        return purchaseReturnRepository.save(existing);
    }

    private void applyHeaderAndItems(PurchaseReturn pr, SavePurchaseReturnRequest request) {
        Supplier supplier = null;
        if (request.supplierId() != null) {
            supplier = supplierRepository.findById(request.supplierId())
                    .filter(s -> tenantGuard.isCurrentStore(s.getStore()))
                    .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + request.supplierId()));
        }
        pr.setSupplier(supplier);
        pr.setDiscountAmount(nz(request.discountAmount()));
        pr.setAmountReceived(nz(request.amountReceived()));
        pr.setNote(request.note());

        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseReturnItemRequest> itemRequests = request.items() != null ? request.items() : List.of();
        for (PurchaseReturnItemRequest itemReq : itemRequests) {
            Product product = productRepository.findById(itemReq.productId())
                    .filter(p -> tenantGuard.isCurrentStore(p.getStore()))
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + itemReq.productId()));
            BigDecimal discount = nz(itemReq.discountAmount());
            BigDecimal lineTotal = itemReq.unitPrice()
                    .multiply(BigDecimal.valueOf(itemReq.quantity()))
                    .subtract(discount);
            pr.addItem(PurchaseReturnItem.builder()
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
        pr.setTotalGoodsValue(total);
        // "Nhà cung cấp cần trả" = tổng tiền hàng trả - giảm giá. Like a
        // receipt's payableAmount it is the gross figure, before whatever the
        // supplier has already refunded (amountReceived) - that lands on the
        // "Tính vào công nợ" line instead.
        pr.setRefundAmount(total.subtract(pr.getDiscountAmount()));
    }

    /**
     * "Hoàn thành" - locks the document and takes the goods off the shelf,
     * under the same pessimistic lock the receipt and POS paths use so two
     * documents touching one product cannot lose an update between them.
     */
    @Transactional
    public PurchaseReturn complete(PurchaseReturn pr, User completedBy) {
        requireDraft(pr, "hoàn thành");
        if (pr.getItems().isEmpty()) {
            throw new IllegalArgumentException("Phiếu trả hàng chưa có hàng hóa nào");
        }
        for (PurchaseReturnItem item : pr.getItems()) {
            if (item.getProduct() == null) {
                throw new IllegalArgumentException(
                        "Hàng hóa \"" + item.getProductName() + "\" đã bị xóa khỏi danh mục, không trả lại được");
            }
            Product product = productRepository.findByIdWithLock(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + item.getProduct().getId()));
            if (product.getStockQuantity() < item.getQuantity()) {
                throw new IllegalArgumentException("Sản phẩm \"" + product.getName()
                        + "\" không đủ tồn kho để trả (còn " + product.getStockQuantity() + ")");
            }
            product.decrementStock(item.getQuantity());
            productRepository.save(product);
        }
        pr.setStatus(PurchaseReturnStatus.COMPLETED);
        pr.setCompletedBy(completedBy);
        pr.setCompletedAt(LocalDateTime.now());
        PurchaseReturn saved = purchaseReturnRepository.save(pr);
        log.info("Purchase return {} completed: {} line(s), stock decremented", saved.getCode(), saved.getItems().size());
        return saved;
    }

    /** "Hủy" - abandons a draft without ever touching stock. */
    @Transactional
    public PurchaseReturn cancel(PurchaseReturn pr) {
        requireDraft(pr, "hủy");
        pr.setStatus(PurchaseReturnStatus.CANCELLED);
        return purchaseReturnRepository.save(pr);
    }

    private void requireDraft(PurchaseReturn pr, String action) {
        if (pr.getStatus() != PurchaseReturnStatus.DRAFT) {
            throw new IllegalStateException("Chỉ có thể " + action + " phiếu trả hàng ở trạng thái Phiếu tạm");
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
