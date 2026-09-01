package com.ut.edu.backend.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dispatches to the right {@link PaymentProvider} by {@link PaymentMethod}.
 * Spring auto-collects every @Component implementing the interface - adding
 * a new gateway (e.g. VNPay) means adding one new class here, nothing else.
 */
@Component
@Slf4j
public class PaymentProviderRegistry {

    private final Map<PaymentMethod, PaymentProvider> providers;

    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(PaymentProvider::getMethod, Function.identity()));
        log.info("Registered payment providers: {}", this.providers.keySet());
    }

    public PaymentProvider get(PaymentMethod method) {
        PaymentProvider provider = providers.get(method);
        if (provider == null) {
            throw new IllegalArgumentException("No payment provider registered for method: " + method);
        }
        return provider;
    }
}
