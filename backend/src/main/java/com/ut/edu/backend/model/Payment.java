package com.ut.edu.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ut.edu.backend.enums.PaymentMethod;
import com.ut.edu.backend.enums.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment entity for tracking payment transactions
 * Integrates with PayPal payment gateway
 */
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_order", columnList = "order_id"),
    @Index(name = "idx_payment_transaction", columnList = "transactionId"),
    @Index(name = "idx_payment_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = "order")
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    @JsonIgnore
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.PAYPAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @NotNull(message = "Payment amount is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";

    // PayPal specific fields
    @Column(unique = true, length = 100)
    private String transactionId; // PayPal transaction ID

    @Column(length = 100)
    private String paypalOrderId; // PayPal order ID

    @Column(length = 100)
    private String paypalPayerId; // PayPal payer ID

    @Column(length = 200)
    private String paypalPayerEmail;

    @Column(columnDefinition = "TEXT")
    private String paymentDetails; // JSON string of full payment details

    private LocalDateTime paymentDate;

    private LocalDateTime refundDate;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(length = 500)
    private String failureReason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Helper methods
    public boolean isPaid() {
        return status == PaymentStatus.COMPLETED;
    }

    public boolean canBeRefunded() {
        return (status == PaymentStatus.COMPLETED || status == PaymentStatus.PARTIALLY_REFUNDED) &&
               (refundAmount == null || refundAmount.compareTo(amount) < 0);
    }

    public void markAsPaid() {
        this.status = PaymentStatus.COMPLETED;
        this.paymentDate = LocalDateTime.now();
    }

    public void markAsFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public void markAsRefunded(BigDecimal refundAmt) {
        this.refundAmount = refundAmt;
        this.refundDate = LocalDateTime.now();

        if (refundAmt.compareTo(amount) >= 0) {
            this.status = PaymentStatus.REFUNDED;
        } else {
            this.status = PaymentStatus.PARTIALLY_REFUNDED;
        }
    }
}
