-- V17: "Chi phí nhập trả NCC" (user-supplied screenshot of a filled-in real
-- KiotViet receipt revealed this is a distinct field from "Tiền trả nhà
-- cung cấp (F8)", which is what PurchaseOrder#amountPaid already models) -
-- an extra charge the supplier itself bills as part of this delivery (e.g.
-- packaging), added on top of totalGoodsValue when computing payableAmount.
-- See PurchaseOrderService for the corrected formula.

ALTER TABLE public.purchase_orders
    ADD COLUMN supplier_charge_amount numeric(14, 2) NOT NULL DEFAULT 0;
