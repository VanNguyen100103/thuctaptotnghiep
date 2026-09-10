package com.ut.edu.backend.shipping.goship;

import com.ut.edu.backend.order.OrderStatus;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Goship's shipment status codes, and what each one means for the order the
 * parcel is carrying.
 *
 * The point of the table is that nobody at the shop should be clicking these.
 * KiotViet calls a carrier's API and lets the carrier drive the order's status;
 * this integration can do the same, because Goship pushes every one of these
 * codes by webhook. Until now the two lived apart - a parcel could be in the
 * courier's hands while the order still read "COD chờ giao", because the only
 * thing that ever moved an order was a person pressing a button.
 *
 * Codes and labels are Goship's own (doc.goship.io, "Shipment status code");
 * the {@link #orderStatus} column is this system's reading of them.
 *
 * A null orderStatus means "say nothing about the order". That is deliberate
 * for the codes that have not settled anything yet: a failed delivery attempt
 * (906) is usually retried, and a parcel on its way back (907) has not arrived
 * back yet. Calling either of those FAILED would be both premature and
 * irreversible - FAILED is terminal, so the 908 that follows would have nothing
 * left to move.
 */
public enum GoshipShipmentStatus {

    // ---- with the shop, or waiting for the courier ----
    DON_MOI(900, "Đơn mới", OrderStatus.PROCESSING),
    CHO_LAY_HANG(901, "Chờ lấy hàng", OrderStatus.PROCESSING),
    LAY_HANG(902, "Lấy hàng", OrderStatus.PROCESSING),

    // ---- out of the shop's hands ----
    /** "Bưu tá đã nhận hàng từ shop" - the parcel has left the premises. */
    DA_LAY_HANG(903, "Đã lấy hàng", OrderStatus.SHIPPED),
    GIAO_HANG(904, "Giao hàng", OrderStatus.SHIPPED),
    DANG_LUU_KHO(918, "Đang lưu kho", OrderStatus.SHIPPED),
    DANG_VAN_CHUYEN(919, "Đang vận chuyển", OrderStatus.SHIPPED),

    // ---- arrived ----
    GIAO_THANH_CONG(905, "Giao thành công", OrderStatus.DELIVERED),
    HOAN_THANH(913, "Hoàn thành", OrderStatus.DELIVERED),

    // ---- did not arrive, and will not ----
    CHUYEN_HOAN(908, "Chuyển hoàn", OrderStatus.FAILED),
    THAT_LAC_HANG(917, "Thất lạc hàng", OrderStatus.FAILED),
    DON_LOI(1000, "Đơn lỗi", OrderStatus.FAILED),
    DON_HUY(914, "Đơn hủy", OrderStatus.CANCELLED),

    // ---- still in play: the parcel moved, the order's answer did not ----
    /** An attempt, not an outcome - the courier normally tries again. */
    GIAO_THAT_BAI(906, "Giao thất bại", null),
    /** On its way back, but not back yet; 908 is what settles it. */
    DANG_CHUYEN_HOAN(907, "Đang chuyển hoàn", null),
    CHAM_LAY_GIAO(915, "Chậm lấy/giao", null),
    GIAO_MOT_PHAN(916, "Giao hàng một phần", null),

    // ---- money moving between Goship, the carrier and the shop, after delivery ----
    DA_DOI_SOAT(909, "Đã đối soát", null),
    DA_DOI_SOAT_KHACH(910, "Đã đối soát khách", null),
    DA_TRA_COD(911, "Đã trả COD cho khách", null),
    CHO_THANH_TOAN_COD(912, "Chờ thanh toán COD", null);

    private static final Map<Integer, GoshipShipmentStatus> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(GoshipShipmentStatus::code, Function.identity()));

    private final int code;
    private final String label;
    private final OrderStatus orderStatus;

    GoshipShipmentStatus(int code, String label, OrderStatus orderStatus) {
        this.code = code;
        this.label = label;
        this.orderStatus = orderStatus;
    }

    public int code() {
        return code;
    }

    /** Goship's own Vietnamese wording, used only when a payload arrives without its label. */
    public String label() {
        return label;
    }

    /** What this means for the order, or null when it means nothing yet. */
    public OrderStatus orderStatus() {
        return orderStatus;
    }

    /** Null for a code Goship has added since this table was written - the shipment still records it, the order just does not react. */
    public static GoshipShipmentStatus of(Integer code) {
        return code == null ? null : BY_CODE.get(code);
    }

    /** The order status a code implies, or null to leave the order alone. */
    public static OrderStatus orderStatusFor(Integer code) {
        GoshipShipmentStatus status = of(code);
        return status == null ? null : status.orderStatus();
    }
}
