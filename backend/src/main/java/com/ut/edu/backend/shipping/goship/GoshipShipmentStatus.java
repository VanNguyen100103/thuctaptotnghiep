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
 * Almost every code now has an order status of its own, because the order's
 * vocabulary is the carrier's. The four that keep a null say nothing about
 * where the parcel is: 915 is a delay flag on wherever it already was, and
 * 909-911 move money between Goship, the carrier and the shop long after the
 * customer has the goods.
 */
public enum GoshipShipmentStatus {

    // ---- with the shop, or waiting for the courier ----
    /** Booked, but Goship has not sent it to the carrier yet - the shop is still packing. */
    DON_MOI(900, "Đơn mới", OrderStatus.PROCESSING),
    CHO_LAY_HANG(901, "Chờ lấy hàng", OrderStatus.AWAITING_PICKUP),
    LAY_HANG(902, "Lấy hàng", OrderStatus.PICKING),

    // ---- out of the shop's hands ----
    /** "Bưu tá đã nhận hàng từ shop" - the parcel has left the premises. */
    DA_LAY_HANG(903, "Đã lấy hàng", OrderStatus.PICKED_UP),
    GIAO_HANG(904, "Đang giao hàng", OrderStatus.SHIPPED),
    DANG_LUU_KHO(918, "Đang lưu kho", OrderStatus.AT_WAREHOUSE),
    DANG_VAN_CHUYEN(919, "Đang vận chuyển", OrderStatus.IN_TRANSIT),

    // ---- did not land this time ----
    /** An attempt, not an outcome - the courier normally tries again, so this is not an ending. */
    GIAO_THAT_BAI(906, "Giao thất bại", OrderStatus.DELIVERY_FAILED),
    GIAO_MOT_PHAN(916, "Giao hàng một phần", OrderStatus.PARTIALLY_DELIVERED),
    /** On its way back, but not back yet; 908 is what settles it. */
    DANG_CHUYEN_HOAN(907, "Đang chuyển hoàn", OrderStatus.RETURNING),

    // ---- arrived ----
    GIAO_THANH_CONG(905, "Giao thành công", OrderStatus.DELIVERED),
    /** After delivery: the courier has the cash and Goship owes it to the shop. */
    CHO_THANH_TOAN_COD(912, "Chờ thanh toán COD", OrderStatus.COD_SETTLEMENT),
    HOAN_THANH(913, "Hoàn thành", OrderStatus.COMPLETED),

    // ---- did not arrive, and will not ----
    CHUYEN_HOAN(908, "Chuyển hoàn", OrderStatus.RETURNED),
    THAT_LAC_HANG(917, "Thất lạc hàng", OrderStatus.LOST),
    DON_HUY(914, "Đơn hủy", OrderStatus.CANCELLED),
    DON_LOI(1000, "Đơn lỗi", OrderStatus.FAILED),

    // ---- says nothing about where the parcel is ----
    /** A delay flag rather than a position: the parcel is wherever it already was. */
    CHAM_LAY_GIAO(915, "Chậm lấy/giao", null),

    // ---- money moving between Goship, the carrier and the shop, after delivery ----
    DA_DOI_SOAT(909, "Đã đối soát", null),
    DA_DOI_SOAT_KHACH(910, "Đã đối soát khách", null),
    DA_TRA_COD(911, "Đã trả COD cho khách", null);

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

    /**
     * Nothing further will happen to the order: it arrived, it came back, or
     * it was called off. What stops the poller asking about a parcel forever.
     *
     * Deliberately not true of the reconciliation codes (909-912): money is
     * still moving between Goship, the carrier and the shop there, but the
     * order's part is over.
     */
    public boolean isFinal() {
        return orderStatus == OrderStatus.COMPLETED
                || orderStatus == OrderStatus.RETURNED
                || orderStatus == OrderStatus.LOST
                || orderStatus == OrderStatus.CANCELLED
                || orderStatus == OrderStatus.FAILED;
    }

    /** The codes after which there is nothing left for the order to learn. */
    public static java.util.List<Integer> finalCodes() {
        return Arrays.stream(values()).filter(GoshipShipmentStatus::isFinal)
                .map(GoshipShipmentStatus::code).collect(Collectors.toList());
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
