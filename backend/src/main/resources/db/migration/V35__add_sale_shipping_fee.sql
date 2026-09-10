-- V35: give a sale a shipping fee of its own.
--
-- The "Bán giao hàng" tab has no field for one, so the cashier has been typing
-- the carrier's price into "Thu khác" - the only box on that screen that adds
-- to what the customer owes. The arithmetic came out right and the label did
-- not: the order then showed "Thu khác 43.000đ" with "Phí giao hàng 0đ"
-- directly beneath it, which is the same number wearing the wrong name next to
-- an empty box wearing the right one.
--
-- V32 added other_collection_amount to orders for exactly this reason, and the
-- reasoning applies again here: a number under a label that does not describe
-- it is worse than one more column.
ALTER TABLE public.sales ADD COLUMN shipping_fee numeric(14,2) NOT NULL DEFAULT 0;

-- Move what has already been collected under the wrong name. Scoped to sales
-- that raised a delivery order, because that is the only place "Thu khác"
-- could have meant the delivery: on a counter sale it is a genuine surcharge
-- and must stay where it is.
UPDATE public.sales sa
SET shipping_fee = sa.other_collection_amount,
    other_collection_amount = 0
FROM public.orders o
WHERE o.sale_id = sa.id
  AND o.sales_channel = 'POS_DELIVERY'
  AND sa.other_collection_amount > 0;

-- The same correction on the orders those sales raised, so the two documents
-- keep telling the same story. Totals are untouched by design - only which
-- line the money is sitting on changes.
UPDATE public.orders o
SET shipping_cost = o.other_collection_amount,
    other_collection_amount = 0
WHERE o.sales_channel = 'POS_DELIVERY'
  AND o.other_collection_amount > 0;

COMMENT ON COLUMN public.sales.shipping_fee IS
    'What the customer is charged for delivery. Separate from what the carrier charges the shop, which lives on the shipment.';
