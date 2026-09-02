-- V10: Product variants (Color x Size). A "variant" is a plain sibling
-- Product row (own sku/price/stock) that shares an opaque grouping key with
-- the other rows generated together. NULL means "not a variant" - today's
-- single-product products are completely unaffected.
-- See AdminProductController#createProductVariants.

ALTER TABLE public.products
    ADD COLUMN variant_group_id VARCHAR(36);

CREATE INDEX idx_products_variant_group ON public.products (variant_group_id);
