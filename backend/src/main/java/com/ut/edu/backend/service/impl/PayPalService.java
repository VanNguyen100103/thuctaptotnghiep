package com.ut.edu.backend.service.impl;

import com.paypal.api.payments.*;
import com.paypal.base.rest.APIContext;
import com.paypal.base.rest.PayPalRESTException;
import com.ut.edu.backend.model.Order;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PayPal Payment Service
 * Handles payment creation, capture, and refunds
 */
@Service
@Slf4j
public class PayPalService {

    @Autowired
    private APIContext apiContext;

    @Value("${paypal.mode}")
    private String mode;

    @Value("${paypal.exchange.rate.vnd-to-usd:25000}")
    private String exchangeRateConfig;

    private static final String CURRENCY = "USD";
    private static final String PAYMENT_METHOD = "paypal";
    private static final String INTENT = "sale";

    /**
     * Format amount for PayPal API
     * CRITICAL: PayPal requires dot (.) as decimal separator, not comma (,)
     * Using US locale to ensure correct format regardless of system locale
     */
    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        symbols.setGroupingSeparator(',');

        DecimalFormat df = new DecimalFormat("0.00", symbols);
        df.setRoundingMode(RoundingMode.HALF_UP);
        df.setGroupingUsed(false);  // Disable grouping (no commas in thousands)

        return df.format(amount);
    }

    /**
     * Convert VND to USD using configured exchange rate
     */
    private BigDecimal convertToUSD(BigDecimal vndAmount) {
        BigDecimal rate = new BigDecimal(exchangeRateConfig);
        return vndAmount.divide(rate, 2, RoundingMode.HALF_UP);
    }

    /**
     * Create PayPal payment
     *
     * @param order Order entity
     * @param successUrl Success redirect URL
     * @param cancelUrl Cancel redirect URL
     * @return Payment object with approval URL
     */
    public Payment createPayment(
            Order order,
            String successUrl,
            String cancelUrl
    ) throws PayPalRESTException {

        // Convert total amount to USD
        // SIMPLIFIED APPROACH: When there's discount, just send total amount
        // This avoids item list validation issues
        BigDecimal totalUSD = convertToUSD(order.getTotal());
        String totalStr = formatAmount(totalUSD);

        log.debug("PayPal payment for order {} - Total: {} VND = {} USD",
                order.getOrderNumber(), order.getTotal(), totalStr);

        // Create amount WITHOUT details to avoid validation issues
        // When details are not provided, PayPal only validates the total
        Amount amount = new Amount();
        amount.setCurrency(CURRENCY);
        amount.setTotal(totalStr);

        // Create transaction
        Transaction transaction = new Transaction();
        transaction.setDescription("Order #" + order.getOrderNumber());
        transaction.setAmount(amount);

        // Note: Item list is optional in PayPal API
        // We skip it to avoid "item amount must match subtotal" validation errors
        // especially when discounts are applied

        List<Transaction> transactions = new ArrayList<>();
        transactions.add(transaction);

        // Create payer
        Payer payer = new Payer();
        payer.setPaymentMethod(PAYMENT_METHOD);

        // Create payment
        Payment payment = new Payment();
        payment.setIntent(INTENT);
        payment.setPayer(payer);
        payment.setTransactions(transactions);

        // Set redirect URLs
        RedirectUrls redirectUrls = new RedirectUrls();
        redirectUrls.setCancelUrl(cancelUrl);
        redirectUrls.setReturnUrl(successUrl);
        payment.setRedirectUrls(redirectUrls);

        try {
            Payment createdPayment = payment.create(apiContext);
            log.info("PayPal payment created successfully for order: {}", order.getOrderNumber());
            return createdPayment;

        } catch (PayPalRESTException e) {
            log.error("Error creating PayPal payment for order: {}", order.getOrderNumber(), e);
            throw e;
        }
    }

    /**
     * Execute/capture payment after approval
     *
     * @param paymentId PayPal payment ID
     * @param payerId PayPal payer ID
     * @return Executed payment
     */
    public Payment executePayment(String paymentId, String payerId) throws PayPalRESTException {
        Payment payment = new Payment();
        payment.setId(paymentId);

        PaymentExecution paymentExecution = new PaymentExecution();
        paymentExecution.setPayerId(payerId);

        try {
            Payment executedPayment = payment.execute(apiContext, paymentExecution);
            log.info("PayPal payment executed successfully: {}", paymentId);
            return executedPayment;

        } catch (PayPalRESTException e) {
            log.error("Error executing PayPal payment: {}", paymentId, e);
            throw e;
        }
    }

    /**
     * Get payment details
     *
     * @param paymentId PayPal payment ID
     * @return Payment object
     */
    public Payment getPaymentDetails(String paymentId) throws PayPalRESTException {
        try {
            Payment payment = Payment.get(apiContext, paymentId);
            log.info("Retrieved PayPal payment details: {}", paymentId);
            return payment;

        } catch (PayPalRESTException e) {
            log.error("Error retrieving PayPal payment details: {}", paymentId, e);
            throw e;
        }
    }

    /**
     * Refund a completed payment
     *
     * @param saleId PayPal sale ID
     * @param amount Amount to refund
     * @return Refund details
     */
    public DetailedRefund refundPayment(String saleId, BigDecimal amount) throws PayPalRESTException {
        Sale sale = new Sale();
        sale.setId(saleId);

        RefundRequest refundRequest = new RefundRequest();
        Amount refundAmount = new Amount();
        refundAmount.setCurrency(CURRENCY);
        refundAmount.setTotal(formatAmount(amount));
        refundRequest.setAmount(refundAmount);

        try {
            DetailedRefund refund = sale.refund(apiContext, refundRequest);
            log.info("PayPal refund processed successfully. Sale ID: {}, Amount: {}", saleId, amount);
            return refund;

        } catch (PayPalRESTException e) {
            log.error("Error processing PayPal refund. Sale ID: {}, Amount: {}", saleId, amount, e);
            throw e;
        }
    }

    /**
     * Get approval URL from payment
     *
     * @param payment Payment object
     * @return Approval URL for redirect
     */
    public String getApprovalUrl(Payment payment) {
        for (Links link : payment.getLinks()) {
            if (link.getRel().equalsIgnoreCase("approval_url")) {
                return link.getHref();
            }
        }
        return null;
    }

    /**
     * Verify webhook signature (for production)
     * Note: Implement webhook signature verification for production
     *
     * @param payload Webhook payload (raw JSON string)
     * @param transmissionId PAYPAL-TRANSMISSION-ID header
     * @param transmissionTime PAYPAL-TRANSMISSION-TIME header
     * @param transmissionSig PAYPAL-TRANSMISSION-SIG header
     * @param certUrl PAYPAL-CERT-URL header
     * @param authAlgo PAYPAL-AUTH-ALGO header
     * @return true if valid
     */
    public boolean verifyWebhookSignature(
            String payload,
            String transmissionId,
            String transmissionTime,
            String transmissionSig,
            String certUrl,
            String authAlgo) {

        // For sandbox mode, log all headers for debugging
        if ("sandbox".equalsIgnoreCase(mode)) {
            log.info("Webhook verification in SANDBOX mode");
            log.info("Transmission ID: {}", transmissionId);
            log.info("Transmission Time: {}", transmissionTime);
            log.info("Transmission Sig: {}", transmissionSig);
            log.info("Cert URL: {}", certUrl);
            log.info("Auth Algo: {}", authAlgo);
            log.info("Payload length: {} chars", payload != null ? payload.length() : 0);

            // In sandbox, we skip signature verification
            // PayPal webhooks in sandbox might not always have valid signatures
            log.warn("⚠️ Webhook signature verification SKIPPED in sandbox mode");
            return true;
        }

        // PRODUCTION MODE: Implement real verification
     
        // https://developer.paypal.com/docs/api/webhooks/v1/#verify-webhook-signature
        //
        // Required steps:
        // 1. Get your webhook ID from PayPal dashboard
        // 2. Make API call to PayPal to verify signature:
        //    POST /v1/notifications/verify-webhook-signature
        //    Body: {
        //      "transmission_id": transmissionId,
        //      "transmission_time": transmissionTime,
        //      "transmission_sig": transmissionSig,
        //      "cert_url": certUrl,
        //      "auth_algo": authAlgo,
        //      "webhook_id": "YOUR_WEBHOOK_ID",
        //      "webhook_event": <parsed payload>
        //    }
        // 3. Check response.verification_status == "SUCCESS"

        log.error("❌ Webhook signature verification NOT IMPLEMENTED for production mode!");
        log.error("This is a SECURITY RISK. Implement proper verification before going live.");
        return false;
    }

    /**
     * Calculate PayPal fee (for reference)
     * Standard rate: 2.9% + $0.30 per transaction
     *
     * @param amount Transaction amount
     * @return Estimated PayPal fee
     */
    public BigDecimal calculatePayPalFee(BigDecimal amount) {
        BigDecimal percentageFee = amount.multiply(new BigDecimal("0.029"));
        BigDecimal fixedFee = new BigDecimal("0.30");
        return percentageFee.add(fixedFee).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get sale ID from executed payment
     *
     * @param payment Executed payment
     * @return Sale ID
     */
    public String getSaleId(Payment payment) {
        if (payment.getTransactions() != null && !payment.getTransactions().isEmpty()) {
            Transaction transaction = payment.getTransactions().get(0);
            if (transaction.getRelatedResources() != null && !transaction.getRelatedResources().isEmpty()) {
                RelatedResources relatedResource = transaction.getRelatedResources().get(0);
                if (relatedResource.getSale() != null) {
                    return relatedResource.getSale().getId();
                }
            }
        }
        return null;
    }
}
