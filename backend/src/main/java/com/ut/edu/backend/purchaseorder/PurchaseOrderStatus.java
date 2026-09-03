package com.ut.edu.backend.purchaseorder;

/** "Trạng thái" of a Nhập hàng (goods receipt) document, matching KiotViet's own 3 states. */
public enum PurchaseOrderStatus {
    /** "Phiếu tạm" - saved but not yet applied to stock; still editable. */
    DRAFT,
    /** "Đã nhập hàng" - stock incremented, locked from further edits. */
    COMPLETED,
    /** "Đã hủy" - abandoned before completion; never touched stock. */
    CANCELLED
}
