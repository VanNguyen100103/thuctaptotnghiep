package com.ut.edu.backend.validation;

import com.ut.edu.backend.order.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusValidatorTest {

    private final OrderStatusValidator validator = new OrderStatusValidator();

    @Test
    void pending_canTransitionTo_pendingCod() {
        assertThat(validator.isValidTransition(OrderStatus.PENDING, OrderStatus.PENDING_COD)).isTrue();
    }

    @Test
    void pendingCod_canTransitionTo_processingCancelledOrRefunded() {
        assertThat(validator.isValidTransition(OrderStatus.PENDING_COD, OrderStatus.PROCESSING)).isTrue();
        assertThat(validator.isValidTransition(OrderStatus.PENDING_COD, OrderStatus.CANCELLED)).isTrue();
        assertThat(validator.isValidTransition(OrderStatus.PENDING_COD, OrderStatus.REFUNDED)).isTrue();
    }

    @Test
    void pendingCod_cannotTransitionTo_shippedOrDelivered() {
        assertThat(validator.isValidTransition(OrderStatus.PENDING_COD, OrderStatus.SHIPPED)).isFalse();
        assertThat(validator.isValidTransition(OrderStatus.PENDING_COD, OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void canAdminSetStatus_pendingCod_isFalse_systemDrivenOnly() {
        assertThat(validator.canAdminSetStatus(OrderStatus.PENDING_COD)).isFalse();
    }

    @Test
    void canAdminSetStatus_processing_isTrue() {
        assertThat(validator.canAdminSetStatus(OrderStatus.PROCESSING)).isTrue();
    }

    @Test
    void pendingCod_isNotTerminal() {
        assertThat(validator.isTerminalStatus(OrderStatus.PENDING_COD)).isFalse();
    }
}
