-- V12: "Mã vạch" (barcode) on products - a plain optional identifier
-- separate from the internal SKU (mã hàng), matching KiotViet's product form.

ALTER TABLE public.products
    ADD COLUMN barcode VARCHAR(100);
