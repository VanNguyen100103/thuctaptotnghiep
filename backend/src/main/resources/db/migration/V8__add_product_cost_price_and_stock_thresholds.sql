-- V8: "Giá vốn" (cost price) and per-product restock thresholds, requested
-- to match KiotViet's product form. cost_price is store-internal only -
-- StorefrontController strips it from public responses in code, it's never
-- exposed via the public /stores/{slug}/products/** endpoints.

ALTER TABLE public.products
    ADD COLUMN cost_price NUMERIC(10, 2),
    ADD COLUMN min_stock_threshold INTEGER,
    ADD COLUMN max_stock_threshold INTEGER;
