-- V18: "Người nhập" - the user who clicked "Hoàn thành" (distinct from
-- "Người tạo", who may have only saved the draft). Captured at completion
-- time in PurchaseOrderService#complete, matching KiotViet's own detail
-- view for a completed goods receipt.

ALTER TABLE public.purchase_orders
    ADD COLUMN completed_by_id bigint REFERENCES public.users(id);
