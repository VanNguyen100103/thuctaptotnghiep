-- V20: wires up the "Điểm" (loyalty points) and "Mã coupon" (coupon code)
-- fields KiotViet shows on the POS payment panel. Points balance lives on
-- the customer (earned from Product#loyaltyPointsEnabled-eligible lines,
-- staged since V13; redeemable 1 point = 1,000 VND); coupons reuse the
-- existing Coupon entity (previously storefront/Order-only) applied
-- directly to a Sale, snapshotted so a completed invoice stays accurate
-- even if the coupon is edited or deactivated later.

ALTER TABLE public.customers
    ADD COLUMN loyalty_points INTEGER NOT NULL DEFAULT 0;

ALTER TABLE public.sales
    ADD COLUMN coupon_code VARCHAR(50),
    ADD COLUMN coupon_discount_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN points_redeemed INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN points_redeemed_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN points_earned INTEGER NOT NULL DEFAULT 0;
