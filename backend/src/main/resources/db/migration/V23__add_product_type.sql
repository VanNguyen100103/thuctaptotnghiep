-- V23: "Loại hàng" (Hàng hóa / Dịch vụ / Combo) on products, matching
-- KiotViet's own product-type field. Descriptive only for now.

ALTER TABLE public.products
    ADD COLUMN product_type VARCHAR(20) NOT NULL DEFAULT 'Hàng hóa';
