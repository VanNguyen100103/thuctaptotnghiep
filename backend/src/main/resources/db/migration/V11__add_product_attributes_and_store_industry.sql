-- V11: generic product attributes (industry-agnostic replacement for the
-- Color x Size-only variant axes) + an optional, purely informational
-- industry classification on Store. See AdminProductController#createProductVariants
-- and Product#attributes.

CREATE TABLE public.product_attributes (
    product_id BIGINT NOT NULL REFERENCES public.products(id),
    attribute_name VARCHAR(100) NOT NULL,
    attribute_value VARCHAR(200) NOT NULL
);
CREATE INDEX idx_product_attributes_product ON public.product_attributes (product_id);

ALTER TABLE public.stores ADD COLUMN industry VARCHAR(100);
