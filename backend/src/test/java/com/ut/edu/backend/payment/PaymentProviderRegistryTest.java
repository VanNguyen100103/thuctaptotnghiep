package com.ut.edu.backend.payment;

import com.ut.edu.backend.order.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProviderRegistryTest {

    private static class FakeProvider implements PaymentProvider {
        private final PaymentMethod method;

        FakeProvider(PaymentMethod method) {
            this.method = method;
        }

        @Override
        public PaymentMethod getMethod() {
            return method;
        }

        @Override
        public PaymentInitiationResult createPayment(Order order, String successUrl, String cancelUrl) {
            return new PaymentInitiationResult("https://example.com", null, "{}");
        }

        @Override
        public PaymentRefundResult refund(Payment payment, BigDecimal amount) {
            return new PaymentRefundResult("ref-1", "{}");
        }
    }

    @Test
    void get_returnsTheRegisteredProviderForItsOwnMethod() {
        FakeProvider paypal = new FakeProvider(PaymentMethod.PAYPAL);
        FakeProvider momo = new FakeProvider(PaymentMethod.MOMO);
        PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of(paypal, momo));

        assertThat(registry.get(PaymentMethod.PAYPAL)).isSameAs(paypal);
        assertThat(registry.get(PaymentMethod.MOMO)).isSameAs(momo);
    }

    @Test
    void get_unregisteredMethod_throwsIllegalArgumentException() {
        PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of(new FakeProvider(PaymentMethod.PAYPAL)));

        assertThatThrownBy(() -> registry.get(PaymentMethod.CREDIT_CARD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CREDIT_CARD");
    }
}
