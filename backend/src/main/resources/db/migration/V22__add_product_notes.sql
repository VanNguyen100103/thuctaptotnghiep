-- V22: "Ghi chú" (internal notes) on products - separate from the public
-- "Mô tả" (description), matching KiotViet's "Mô tả, ghi chú" tab which
-- shows both. Store-internal, optional, no default.

ALTER TABLE public.products
    ADD COLUMN notes TEXT;
