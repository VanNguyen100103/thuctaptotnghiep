-- V7: Add PENDING_COD to orders.status CHECK constraint.
-- COD confirms itself synchronously in PaymentController.createPayment()
-- (see CodPaymentProvider / PaymentProvider#confirmsImmediately) and lands
-- directly in PENDING_COD instead of PAYMENT_PENDING - fulfillment-committed,
-- stock decremented, but cash not yet collected (that happens at delivery;
-- see AdminOrderController#updateOrderStatus flipping Payment.status on the
-- DELIVERED transition). Without this, inserting/updating an Order row with
-- status=PENDING_COD fails at the DB layer even though the Java enum accepts
-- it - same class of bug V6 fixed for payments.payment_method=MOMO.

ALTER TABLE public.orders DROP CONSTRAINT orders_status_check;

ALTER TABLE public.orders ADD CONSTRAINT orders_status_check
    CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAYMENT_PENDING'::character varying, 'PENDING_COD'::character varying, 'PAID'::character varying, 'PROCESSING'::character varying, 'SHIPPED'::character varying, 'DELIVERED'::character varying, 'CANCELLED'::character varying, 'REFUNDED'::character varying, 'FAILED'::character varying])::text[])));
