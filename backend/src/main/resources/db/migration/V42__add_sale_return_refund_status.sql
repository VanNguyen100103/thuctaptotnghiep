-- V42: does a Trả hàng receipt's money actually have to leave the till yet?
--
-- V40 recorded what the shop owes the customer back, and stopped there. That
-- made every return look identical whether or not the refund had been paid,
-- which is fine for cash (the drawer opens at the counter and the customer
-- walks away paid) and wrong for a bank transfer: SePay has no API to send
-- money (see SePayPaymentProvider#refund), so the owner makes that transfer
-- by hand in their banking app, minutes or hours later. Until this column
-- existed there was no way to answer "which customers am I still holding
-- money for".
--
-- PENDING is therefore only ever the starting state of a transfer refund
-- worth more than nothing. It clears one of two ways: the SePay webhook sees
-- an outgoing transfer carrying this receipt's "TH<id>" content (the same
-- trick orders already use with "DH<id>" - see PaymentController), or the
-- shop marks it paid by hand, which has to stay possible because a webhook
-- configured for "Tiền vào" only, or a mistyped content, would otherwise
-- strand the receipt forever.

ALTER TABLE public.sale_returns
    ADD COLUMN refund_status character varying(20) NOT NULL DEFAULT 'REFUNDED',
    -- When the money actually reached the customer, as opposed to created_at,
    -- which is when the goods came back over the counter.
    ADD COLUMN refunded_at timestamp(6) without time zone,
    -- SePay's own reference for the outgoing transfer, when the webhook is
    -- what settled it; null when a person ticked it off instead.
    ADD COLUMN refund_reference character varying(200);

CREATE INDEX idx_sale_returns_refund_status ON public.sale_returns (refund_status);

-- Backfill, rather than letting the DEFAULT stand for every existing row.
-- Cash/card/wallet refunds were handed over at the counter the moment the
-- receipt was written, so created_at is when they were settled. A transfer
-- refund written before this column existed has NOT been reconciled by
-- anything, so it goes to PENDING and shows up in the shop's "còn nợ khách"
-- list - which is the honest answer, and the whole point of the column.
UPDATE public.sale_returns
   SET refunded_at = created_at
 WHERE refund_method <> 'BANK_TRANSFER' OR refund_amount <= 0;

UPDATE public.sale_returns
   SET refund_status = 'PENDING'
 WHERE refund_method = 'BANK_TRANSFER' AND refund_amount > 0;
