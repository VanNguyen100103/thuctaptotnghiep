package com.ut.edu.backend.purchasereturn;

/** "Trạng thái" of a Trả hàng nhập document - the same three states a Nhập hàng document moves through, with KiotViet's own wording for the middle one. */
public enum PurchaseReturnStatus {
    /** "Phiếu tạm" - saved but not yet applied to stock; still editable. */
    DRAFT,
    /** "Đã trả hàng" - stock decremented, locked from further edits. */
    COMPLETED,
    /** "Đã hủy" - abandoned before completion; never touched stock. */
    CANCELLED
}
