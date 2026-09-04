package com.ut.edu.backend.payment;

import com.ut.edu.backend.order.Order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * VietQR bank transfer via SePay. Unlike PayPal/MoMo there is no gateway API
 * call to create a payment - SePay watches the linked personal/business bank
 * account for incoming transfers and posts a webhook when one arrives (see
 * PaymentController's /payments/webhook/sepay). createPayment() only builds
 * a VietQR image URL (vietqr.app/img - a public, unauthenticated endpoint;
 * account/bank/amount/content are embedded as query params, so the frontend
 * can both render it as an <img> and read the params back out for a manual
 * "or transfer to this account" fallback).
 *
 * PaymentInitiationResult#redirectUrl is reused as "where to find the
 * payment UI", same slot MoMo/PayPal use for their redirect URLs - the
 * frontend just treats a BANK_TRANSFER result as a QR image src instead of
 * a page to navigate to (see PaymentProvider#confirmsImmediately's doc for
 * the same kind of per-provider reinterpretation already established there).
 */
@Component
public class SePayPaymentProvider implements PaymentProvider {

    private static final String QR_ENDPOINT = "https://vietqr.app/img";

    @Value("${sepay.account-number}")
    private String accountNumber;

    @Value("${sepay.bank-code}")
    private String bankCode;

    @Value("${sepay.account-name:}")
    private String accountName;

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.BANK_TRANSFER;
    }

    @Override
    public PaymentInitiationResult createPayment(Order order, String successUrl, String cancelUrl) {
        // Short, digits-only content: VietQR's "des" field commonly gets
        // truncated to ~20-25 chars by banking apps and some strip anything
        // that isn't a letter/digit, so the full ORD-<timestamp>-<random>
        // orderNumber is too fragile to round-trip through a real transfer.
        // "DH" (đơn hàng) + the numeric order id is short and always
        // preserved - the webhook below extracts it with the same pattern.
        String content = "DH" + order.getId();
        String qrUrl = buildQrUrl(order.getTotal(), content);
        String amount = order.getTotal().setScale(0, RoundingMode.HALF_UP).toBigInteger().toString();

        String rawJson = """
                {"provider":"sepay","qrUrl":"%s","accountNumber":"%s","bankCode":"%s","content":"%s","amount":"%s"}
                """.formatted(qrUrl, accountNumber, bankCode, content, amount).strip();

        return new PaymentInitiationResult(qrUrl, content, rawJson);
    }

    /**
     * Builds a VietQR image URL for an arbitrary amount/content, without an
     * Order - used directly by the POS split-tender "Chuyển khoản" QR button
     * (PaymentController#getVietQr), which has nothing to match a webhook
     * against yet (a POS sale isn't recorded until checkout completes). It's
     * purely a display convenience for the cashier to show the customer; the
     * cashier confirms the transfer arrived by eye, the same way they would
     * with a QR code taped to the counter.
     */
    public String buildQrUrl(BigDecimal amount, String content) {
        if (accountNumber == null || accountNumber.isBlank() || bankCode == null || bankCode.isBlank()) {
            throw new SePayApiException(
                    "SePay is not configured - set SEPAY_ACCOUNT_NUMBER and SEPAY_BANK_CODE");
        }

        String amountStr = amount.setScale(0, RoundingMode.HALF_UP).toBigInteger().toString();

        return UriComponentsBuilder.fromHttpUrl(QR_ENDPOINT)
                .queryParam("acc", accountNumber)
                .queryParam("bank", bankCode)
                .queryParam("amount", amountStr)
                .queryParamIfPresent("des", content != null && !content.isBlank()
                        ? java.util.Optional.of(content) : java.util.Optional.empty())
                .queryParam("template", "compact")
                .queryParamIfPresent("holder", accountName != null && !accountName.isBlank()
                        ? java.util.Optional.of(accountName) : java.util.Optional.empty())
                .build()
                .toUriString();
    }

    @Override
    public PaymentRefundResult refund(Payment payment, BigDecimal amount) {
        throw new RefundNotSupportedException(
                "SePay has no refund API - it only observes incoming transfers. Transfer the refund back manually and record it");
    }
}
