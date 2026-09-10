-- V36: let an order's status say what the carrier says.
--
-- Until now this system folded Goship's codes into five: 903 "Đã lấy hàng",
-- 904 "Đang giao hàng", 918 "Đang lưu kho" and 919 "Đang vận chuyển" all
-- arrived as SHIPPED. That is the right grain for a lifecycle and the wrong one
-- for a shop chasing a parcel, which asks the carrier's question - has it been
-- picked up, is it sitting in a warehouse, is it out with a courier.
--
-- So the carrier's vocabulary joins this system's own. The first few statuses
-- stay ours, because an order that has no parcel yet cannot be described by a
-- carrier at all.
--
-- No rows change. Every existing value survives with the meaning it had; the
-- constraint only widens.

ALTER TABLE public.orders DROP CONSTRAINT orders_status_check;

ALTER TABLE public.orders ADD CONSTRAINT orders_status_check
    CHECK (((status)::text = ANY ((ARRAY[
        -- before there is a parcel
        'PENDING'::character varying,
        'PAYMENT_PENDING'::character varying,
        'PENDING_COD'::character varying,
        'PAID'::character varying,
        'PROCESSING'::character varying,
        -- the carrier's, one per Goship code
        'AWAITING_PICKUP'::character varying,
        'PICKING'::character varying,
        'PICKED_UP'::character varying,
        'AT_WAREHOUSE'::character varying,
        'IN_TRANSIT'::character varying,
        'SHIPPED'::character varying,
        'DELIVERY_FAILED'::character varying,
        'PARTIALLY_DELIVERED'::character varying,
        'RETURNING'::character varying,
        'DELIVERED'::character varying,
        'COD_SETTLEMENT'::character varying,
        -- endings
        'COMPLETED'::character varying,
        'RETURNED'::character varying,
        'LOST'::character varying,
        'CANCELLED'::character varying,
        'REFUNDED'::character varying,
        'FAILED'::character varying
    ])::text[])));

COMMENT ON COLUMN public.orders.status IS
    'Two vocabularies in one column: this system''s own for an order with no parcel yet, and Goship''s from the moment there is one. See OrderStatus and GoshipShipmentStatus.';
