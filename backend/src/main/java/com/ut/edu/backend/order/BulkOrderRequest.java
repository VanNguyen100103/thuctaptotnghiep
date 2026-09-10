package com.ut.edu.backend.order;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * The body behind KiotViet's selection toolbar - every action on it ("Xử lý
 * đặt hàng", "Kết thúc", "Hủy đơn", "Gộp đơn", the bulk edit behind "...")
 * works on the rows that are ticked, so they all start from a list of ids.
 *
 * Which of the optional fields matter depends on the endpoint: a bulk edit
 * reads recipientName/salesChannel/notes and treats null as "leave alone"
 * (which is how a partially-filled edit form has to behave), a cancel reads
 * reason, and the rest read neither.
 */
public record BulkOrderRequest(
        @NotEmpty(message = "Chưa chọn đơn đặt hàng nào") List<Long> ids,
        /** "Người nhận đặt". Null leaves each order's own; blank clears it. */
        String recipientName,
        /** "Kênh bán". */
        String salesChannel,
        /** "Ghi chú". */
        String notes,
        /** "Lý do hủy" - written into the order's admin notes so the why survives the cancel. */
        String reason) {
}
