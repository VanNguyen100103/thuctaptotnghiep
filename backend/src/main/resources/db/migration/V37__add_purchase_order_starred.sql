-- V37: the star column on the Nhập hàng list.
--
-- V32 gave orders one and the Đặt hàng list has drawn it ever since; the goods
-- receipt list is the same KiotViet table with the same first two columns, so
-- it needs the same flag behind it. Purely a per-shop bookmark - nothing reads
-- it but the list.

ALTER TABLE public.purchase_orders ADD COLUMN starred boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN public.purchase_orders.starred IS
    'Đánh dấu - the shop bookmarked this receipt on the Nhập hàng list. No effect on stock or accounting.';
