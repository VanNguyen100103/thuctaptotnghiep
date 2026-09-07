package com.ut.edu.backend.payment;

import com.ut.edu.backend.exception.ResourceNotFoundException;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.user.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Creates and settles the counter QR sessions described in the V27 migration.
 *
 * The whole point is the {@code reference}: one bank account can serve every
 * counter payment and the storefront at once, because what identifies a
 * transfer is its content, not the account it landed in. Virtual accounts
 * would do the same job, but they are capped per SePay plan and this is not.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PosPaymentSessionService {

    /**
     * How long the terminal keeps polling. Long enough for a customer to
     * fumble with their banking app, short enough that a cashier is not
     * left staring at a dead screen. Money arriving later is still
     * accepted (see {@link #settleFromWebhook}), it just needs the cashier
     * to look it up rather than the screen reacting on its own.
     */
    private static final int SESSION_TTL_MINUTES = 15;

    /** "POS" + 9 digits: 12 chars, safely inside the ~25 banking apps keep, and no punctuation for them to strip. */
    private static final String REFERENCE_PREFIX = "POS";
    private static final int REFERENCE_DIGITS = 9;
    private static final int REFERENCE_ATTEMPTS = 5;

    private final PosPaymentSessionRepository sessionRepository;
    private final SePayPaymentProvider sePayPaymentProvider;
    private final TenantGuard tenantGuard;
    private final SecureRandom random = new SecureRandom();

    /**
     * Reserves a reference for an amount and hands back the row the terminal
     * will poll. Throws SePayApiException (via the provider) when SePay is
     * not configured, rather than storing a session no QR can be built for.
     */
    @Transactional
    public PosPaymentSession create(User cashier, BigDecimal amount) {
        String reference = generateReference();
        // Fail before the insert if the account isn't configured - a session
        // whose QR can't be rendered is worse than no session at all.
        sePayPaymentProvider.buildQrUrl(amount, reference);

        PosPaymentSession session = PosPaymentSession.builder()
                .store(tenantGuard.currentStoreRef())
                .reference(reference)
                .amount(amount)
                .status(PosPaymentSessionStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(SESSION_TTL_MINUTES))
                .createdBy(cashier)
                .build();

        PosPaymentSession saved = sessionRepository.save(session);
        log.info("POS payment session {} created for {} (reference {})", saved.getId(), amount, reference);
        return saved;
    }

    /** The QR image the cashier shows - regenerated on read rather than stored, so a change of bank account applies to sessions already open. */
    public String qrUrl(PosPaymentSession session) {
        return sePayPaymentProvider.buildQrUrl(session.getAmount(), session.getReference());
    }

    /** Tenant-checked read for the terminal's polling. findById bypasses the Hibernate filter, hence the explicit store check. */
    @Transactional(readOnly = true)
    public PosPaymentSession getForCurrentStore(Long id) {
        PosPaymentSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên thanh toán"));
        tenantGuard.requireSameStore(session.getStore());
        return session;
    }

    /**
     * Webhook path: an incoming transfer whose content matched a session.
     *
     * A bank transfer, unlike a gateway call, can arrive short - so an
     * amount below what was asked records what came in but leaves the
     * session PENDING, which is what lets the terminal show "còn thiếu ..."
     * instead of silently hanging. Assignment rather than accumulation
     * keeps SePay's webhook redeliveries idempotent; a genuine second
     * top-up transfer is rare enough at a counter to be worth handling by
     * eye rather than by guessing which of the two a repeat delivery is.
     *
     * Returns true when this call is what flipped the session to PAID.
     */
    @Transactional
    public boolean settleFromWebhook(String reference, BigDecimal transferredAmount, String sepayTransactionId) {
        PosPaymentSession session = sessionRepository.findByReference(reference).orElse(null);
        if (session == null) {
            log.warn("SePay webhook referenced POS session {} which does not exist", reference);
            return false;
        }
        if (session.getStatus() == PosPaymentSessionStatus.PAID) {
            log.info("POS session {} already PAID, ignoring redelivery", reference);
            return false;
        }
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            // Accepted anyway - the money is in the account either way, and
            // dropping it would leave the shop owner reconciling by hand.
            // The cashier's screen has stopped watching by now, so this line
            // is how the payment gets found again.
            log.warn("POS session {} was paid after its polling window closed - the terminal will not have reacted", reference);
        }

        session.setTransferredAmount(transferredAmount);
        session.setSepayTransactionId(sepayTransactionId);

        if (transferredAmount.compareTo(session.getAmount()) < 0) {
            log.warn("POS session {} received {} of {} - left PENDING for the cashier to resolve",
                    reference, transferredAmount, session.getAmount());
            sessionRepository.save(session);
            return false;
        }

        session.setStatus(PosPaymentSessionStatus.PAID);
        session.setPaidAt(LocalDateTime.now());
        sessionRepository.save(session);
        log.info("POS session {} PAID: received {} for {}", reference, transferredAmount, session.getAmount());
        return true;
    }

    /** Random rather than sequential so a customer mistyping one digit lands on nothing instead of somebody else's open QR. */
    private String generateReference() {
        for (int attempt = 0; attempt < REFERENCE_ATTEMPTS; attempt++) {
            StringBuilder sb = new StringBuilder(REFERENCE_PREFIX);
            for (int i = 0; i < REFERENCE_DIGITS; i++) {
                sb.append(random.nextInt(10));
            }
            String candidate = sb.toString();
            if (!sessionRepository.existsByReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique POS payment reference");
    }
}
