-- V13: "Vị trí" (storage location), "Trọng lượng" (weight), "Kích thước"
-- (width/length/height) and "Tích điểm" (loyalty-points-eligible) on
-- products - matches KiotViet's product form's "Vị trí, trọng lượng, kích
-- thước" section and its standalone "Tích điểm" toggle. All optional,
-- store-internal fields; nothing downstream reads them yet (same staged-
-- ahead-of-the-feature treatment as Store#industry in V11).

ALTER TABLE public.products
    ADD COLUMN location VARCHAR(255),
    ADD COLUMN weight NUMERIC(10, 2),
    ADD COLUMN weight_unit VARCHAR(10),
    ADD COLUMN width NUMERIC(10, 2),
    ADD COLUMN length NUMERIC(10, 2),
    ADD COLUMN height NUMERIC(10, 2),
    ADD COLUMN dimension_unit VARCHAR(10),
    ADD COLUMN loyalty_points_enabled BOOLEAN NOT NULL DEFAULT TRUE;
