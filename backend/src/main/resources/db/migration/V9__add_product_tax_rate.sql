-- V9: "Thuế suất" (VAT %) on products, OWNER-only (see AdminProductController -
-- MANAGER-submitted values are silently ignored). tax_rate is store-internal
-- only, same treatment as cost_price - stripped from every public response
-- by hideInternalFields() in ProductController/StorefrontController.

ALTER TABLE public.products
    ADD COLUMN tax_rate NUMERIC(5, 2);
