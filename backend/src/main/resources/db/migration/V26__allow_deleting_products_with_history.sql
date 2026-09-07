-- "Xóa" on the product list is a real delete, but a product that has been
-- ordered, sold at the counter, or received on a purchase order must not
-- take those records down with it.
--
-- Every one of these line-item tables already snapshots what it needs at
-- transaction time (product_name, product_sku, unit price, quantity), so
-- the link back to the live product is a convenience, not the record
-- itself. Making it nullable lets a delete clear the link and leave the
-- history complete and readable.
ALTER TABLE public.order_items ALTER COLUMN product_id DROP NOT NULL;
ALTER TABLE public.sale_items ALTER COLUMN product_id DROP NOT NULL;
ALTER TABLE public.purchase_order_items ALTER COLUMN product_id DROP NOT NULL;
