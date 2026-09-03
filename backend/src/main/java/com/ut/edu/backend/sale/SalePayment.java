package com.ut.edu.backend.sale;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One tender line of a (possibly split) payment - e.g. a sale paid partly in
 * cash and partly by bank transfer has two SalePayment rows. This is the
 * concrete mechanism behind KiotViet's "Thanh toán nhiều phương thức"
 * dialog: {@link Sale} can carry any number of these, unlike the online
 * storefront's {@link com.ut.edu.backend.payment.Payment}, which is
 * one-to-one with its Order.
 */
@Entity
@Table(name = "sale_payments", indexes = {
    @Index(name = "idx_sale_payments_sale", columnList = "sale_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = "sale")
public class SalePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    @JsonIgnore
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalePaymentMethod method;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
}
