-- V24: "Điểm" - flat loyalty points earned per unit sold, overriding
-- SaleService's default 10,000 VND = 1 point rate when set. Matches
-- KiotViet's product form's "Điểm" field next to the "Tích điểm" toggle
-- (see V13 for loyalty_points_enabled).

ALTER TABLE public.products
    ADD COLUMN loyalty_points INT;
