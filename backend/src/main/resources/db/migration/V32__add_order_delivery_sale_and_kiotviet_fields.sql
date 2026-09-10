-- V32: let "Đặt hàng" hold a delivery order rung up at the register, and give
-- the screen the columns KiotViet's own order list filters and edits on.
--
-- V19 deliberately kept POS sales out of Order: an Order demanded a
-- registered User and a complete postal shipping address, and a walk-in sale
-- has neither. That reasoning still holds for "Bán thường" - a counter sale
-- is an invoice and nothing else. It does not hold for "Bán giao hàng", which
-- collects a named recipient, a phone and a Tỉnh/Quận/Phường address before
-- it will finalize: that IS an order, and the shop looks for it under Đặt
-- hàng. So the two NOT NULLs that blocked it are relaxed rather than fed
-- placeholder data (postal code and country are not asked for anywhere in
-- the Vietnamese address flow, and a POS order's buyer is a Customer, not a
-- User).

ALTER TABLE public.orders ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE public.orders ALTER COLUMN shipping_postal_code DROP NOT NULL;
ALTER TABLE public.orders ALTER COLUMN shipping_country DROP NOT NULL;

-- The walk-in buyer behind a register order. Mutually exclusive with user_id
-- in practice - a storefront order has an account, a POS one has a Customer
-- card - but not enforced as such: an order simply names whoever it has.
ALTER TABLE public.orders ADD COLUMN customer_id bigint REFERENCES public.customers(id);

-- The invoice this order was rung up as. Present only for a "Bán giao hàng"
-- order, which is paid at the register before it ships, so the money on it
-- lives on the Sale rather than on a gateway Payment.
ALTER TABLE public.orders ADD COLUMN sale_id bigint REFERENCES public.sales(id);

-- "Người tạo" - the staff member who rang it up. Null for a storefront order:
-- the customer created that one, and the buyer is already on user_id.
ALTER TABLE public.orders ADD COLUMN created_by_id bigint REFERENCES public.users(id);

-- "Người nhận" - who the parcel is addressed to, which is not always who
-- ordered it. Storefront checkout has no field for it, so it stays null there
-- and the buyer's name stands in.
ALTER TABLE public.orders ADD COLUMN recipient_name character varying(200);

-- Phường/Xã. shipping_city already holds Tỉnh/TP and shipping_state_province
-- Quận/Huyện (see the storefront checkout form's own labels); the third level
-- had nowhere to go until now.
ALTER TABLE public.orders ADD COLUMN shipping_ward character varying(100);

-- "Kênh bán" - where the order came from. KiotViet prefixes its order codes
-- by channel (DHZMA... from Zalo, DHFBP... from a Facebook page); this is the
-- same fact stored rather than encoded into the code.
ALTER TABLE public.orders ADD COLUMN sales_channel character varying(30) NOT NULL DEFAULT 'STOREFRONT';

-- The ★ column: one flag per order, the shop's own "come back to this".
ALTER TABLE public.orders ADD COLUMN starred boolean NOT NULL DEFAULT false;

-- "Thời gian giao hàng" - when the shop promised it, which the sidebar filters
-- on separately from when the order was placed.
ALTER TABLE public.orders ADD COLUMN expected_delivery_at timestamp(6) without time zone;

-- "Thu khác" - the register's catch-all surcharge, which an Order had no
-- column for: every other money field on it is either a reduction or one
-- named cost. Folding it into tax or shipping would have put a number under a
-- label that does not describe it, so it gets its own, and calculateTotal()
-- adds it the way the register does.
ALTER TABLE public.orders ADD COLUMN other_collection_amount numeric(10,2) NOT NULL DEFAULT 0;

-- "Gộp đơn": the surviving order several others were folded into. The sources
-- are cancelled rather than deleted, so the merge stays auditable and the
-- customer's old codes still resolve.
ALTER TABLE public.orders ADD COLUMN merged_into_order_id bigint REFERENCES public.orders(id);

ALTER TABLE public.orders ADD CONSTRAINT orders_sales_channel_check
    CHECK (((sales_channel)::text = ANY ((ARRAY['STOREFRONT'::character varying, 'DIRECT'::character varying, 'FACEBOOK'::character varying, 'ZALO'::character varying, 'SHOPEE'::character varying, 'LAZADA'::character varying, 'TIKTOK'::character varying, 'OTHER'::character varying])::text[])));

CREATE INDEX idx_orders_customer ON public.orders (customer_id);
CREATE INDEX idx_orders_sale ON public.orders (sale_id);
CREATE INDEX idx_orders_created_by ON public.orders (created_by_id);

COMMENT ON COLUMN public.orders.sale_id IS
    'The Sale a "Bán giao hàng" order was rung up as. Null for a storefront order, which settles through payments instead.';

-- "Mã đặt hàng" becomes per-shop. A register order is numbered DH000001 the
-- way KiotViet numbers its own, and that sequence restarts in every store -
-- which a globally unique order_number would have made impossible (shop B's
-- first order would collide with shop A's). Storefront's ORD-<timestamp>-
-- <random> codes are unaffected: they were never going to collide either way.
ALTER TABLE public.orders DROP CONSTRAINT IF EXISTS uknthkiu7pgmnqnu86i2jyoe2v7;

ALTER TABLE public.orders
    ADD CONSTRAINT uk_orders_store_order_number UNIQUE (store_id, order_number);
