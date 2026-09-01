-- V6: Add MOMO to the payments.payment_method CHECK constraint.
-- Without this, inserting a Payment row with paymentMethod=MOMO fails at
-- the DB layer even though the Java enum accepts it.

ALTER TABLE public.payments DROP CONSTRAINT payments_payment_method_check;

ALTER TABLE public.payments ADD CONSTRAINT payments_payment_method_check
    CHECK (((payment_method)::text = ANY ((ARRAY['PAYPAL'::character varying, 'CREDIT_CARD'::character varying, 'DEBIT_CARD'::character varying, 'BANK_TRANSFER'::character varying, 'CASH_ON_DELIVERY'::character varying, 'MOMO'::character varying])::text[])));
