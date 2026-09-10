-- V33: tie a booked shipment to the order it is carrying, and record who is
-- paying for it.
--
-- V32 gave "Đặt hàng" a row for every "Bán giao hàng" sale, but the row could
-- only say which carrier was picked. Everything else the cashier chose on the
-- delivery panel - the service level, how long it was expected to take, the
-- 43.000đ the carrier charges, whether the shop or the recipient is paying
-- that, the COD to collect, the tracking code - was already being stored, one
-- table over, on the shipment. It just had nothing pointing at it.
--
-- So this is a foreign key rather than six more columns on orders. Copying
-- those values onto the order would have meant two records of one fact that
-- drift the moment Goship's webhook revises the fee or fills in the tracking
-- code, which it does for every booking.
ALTER TABLE public.shipments ADD COLUMN order_id bigint REFERENCES public.orders(id);

-- "Người gửi trả phí". The booking request has always carried it - Goship is
-- told, the carrier bills accordingly - but nothing kept it afterwards, so a
-- shipment could not answer who owed its fee. The default matches
-- CreateShipmentRequest's own reading of a null: the register has already told
-- the customer what they owe, so billing them again at the door has to be a
-- deliberate choice.
ALTER TABLE public.shipments ADD COLUMN sender_pays_shipping boolean NOT NULL DEFAULT true;

CREATE INDEX idx_shipments_order ON public.shipments (order_id);

COMMENT ON COLUMN public.shipments.order_id IS
    'The Đặt hàng row this parcel belongs to. Null for a shipment booked outside an order.';

-- Backfill: shipments booked before this migration already say which sale they
-- belong to, in the note the register writes on every booking ("Đơn hàng
-- HD000019", optionally followed by the cashier's own note). That is enough to
-- find the order, since V32 linked each register order to its sale.
--
-- Matched exactly rather than by prefix: "Đơn hàng HD000019%" would also match
-- HD0000199, and hanging a parcel off the wrong order is worse than leaving it
-- unlinked.
UPDATE public.shipments s
SET order_id = o.id
FROM public.orders o
JOIN public.sales sa ON sa.id = o.sale_id
WHERE s.order_id IS NULL
  AND s.store_id = o.store_id
  AND (s.note = 'Đơn hàng ' || sa.code OR s.note LIKE 'Đơn hàng ' || sa.code || ' - %');
