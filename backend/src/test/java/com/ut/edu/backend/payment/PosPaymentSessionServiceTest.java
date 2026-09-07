package com.ut.edu.backend.payment;

import com.ut.edu.backend.store.TenantGuard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The settlement rules a counter QR turns on: what counts as paid, what a
 * short transfer does, and what happens when SePay redelivers a webhook.
 */
@ExtendWith(MockitoExtension.class)
class PosPaymentSessionServiceTest {

    @Mock
    private PosPaymentSessionRepository sessionRepository;
    @Mock
    private SePayPaymentProvider sePayPaymentProvider;
    @Mock
    private TenantGuard tenantGuard;

    private PosPaymentSessionService service;
    private PosPaymentSession session;

    @BeforeEach
    void setUp() {
        service = new PosPaymentSessionService(sessionRepository, sePayPaymentProvider, tenantGuard);
        session = PosPaymentSession.builder()
                .id(7L)
                .reference("POS123456789")
                .amount(new BigDecimal("41000.00"))
                .status(PosPaymentSessionStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
    }

    private void sessionExists() {
        when(sessionRepository.findByReference("POS123456789")).thenReturn(Optional.of(session));
    }

    @Test
    void exactTransfer_marksPaid() {
        sessionExists();

        boolean settled = service.settleFromWebhook("POS123456789", new BigDecimal("41000"), "FT2609");

        assertThat(settled).isTrue();
        assertThat(session.getStatus()).isEqualTo(PosPaymentSessionStatus.PAID);
        assertThat(session.getTransferredAmount()).isEqualByComparingTo("41000");
        assertThat(session.getSepayTransactionId()).isEqualTo("FT2609");
        assertThat(session.getPaidAt()).isNotNull();
        verify(sessionRepository).save(session);
    }

    @Test
    void overpayment_stillMarksPaid() {
        // A customer rounding 41.000 up to 50.000 has still paid; the change
        // is the cashier's to hand back, not a reason to hold the sale.
        sessionExists();

        assertThat(service.settleFromWebhook("POS123456789", new BigDecimal("50000"), "FT1")).isTrue();
        assertThat(session.getStatus()).isEqualTo(PosPaymentSessionStatus.PAID);
        assertThat(session.getTransferredAmount()).isEqualByComparingTo("50000");
    }

    @Test
    void shortPayment_recordsAmountButStaysPending() {
        sessionExists();

        boolean settled = service.settleFromWebhook("POS123456789", new BigDecimal("40000"), "FT2");

        assertThat(settled).isFalse();
        assertThat(session.getStatus()).isEqualTo(PosPaymentSessionStatus.PENDING);
        // Recorded anyway, so the terminal can say how much is still missing
        // instead of just hanging on "đang chờ".
        assertThat(session.getTransferredAmount()).isEqualByComparingTo("40000");
        verify(sessionRepository).save(session);
    }

    @Test
    void redelivery_ofAPaidSession_changesNothing() {
        session.setStatus(PosPaymentSessionStatus.PAID);
        session.setTransferredAmount(new BigDecimal("41000.00"));
        session.setPaidAt(LocalDateTime.now().minusMinutes(1));
        sessionExists();

        assertThat(service.settleFromWebhook("POS123456789", new BigDecimal("41000"), "FT1")).isFalse();
        assertThat(session.getTransferredAmount()).isEqualByComparingTo("41000.00");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void lateTransfer_isStillAccepted() {
        // The terminal has stopped watching, but the money is in the account -
        // dropping it would leave the owner reconciling by hand.
        session.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        sessionExists();

        assertThat(service.settleFromWebhook("POS123456789", new BigDecimal("41000"), "FT3")).isTrue();
        assertThat(session.getStatus()).isEqualTo(PosPaymentSessionStatus.PAID);
    }

    @Test
    void unknownReference_isIgnored() {
        // Not every transfer on this bank account is one of ours.
        when(sessionRepository.findByReference("POS999999999")).thenReturn(Optional.empty());

        assertThat(service.settleFromWebhook("POS999999999", new BigDecimal("41000"), "FT4")).isFalse();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void create_reservesADistinctReferenceAndEmbedsItInTheQr() {
        when(sessionRepository.existsByReference(anyString())).thenReturn(false);
        when(sessionRepository.save(any(PosPaymentSession.class))).thenAnswer(inv -> inv.getArgument(0));

        PosPaymentSession created = service.create(null, new BigDecimal("41000"));

        assertThat(created.getReference()).matches("POS\\d{9}");
        assertThat(created.getStatus()).isEqualTo(PosPaymentSessionStatus.PENDING);
        assertThat(created.getExpiresAt()).isAfter(LocalDateTime.now());

        // The QR must be built for the same reference the row reserved -
        // that pairing is the only thing tying a transfer back to this sale.
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(sePayPaymentProvider).buildQrUrl(any(BigDecimal.class), content.capture());
        assertThat(content.getValue()).isEqualTo(created.getReference());
    }
}
