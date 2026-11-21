package com.fooddelivery.backend.unit;

import com.fooddelivery.backend.entity.OrderStatus;
import com.fooddelivery.backend.exception.BadRequestException;
import com.fooddelivery.backend.statemachine.OrderStateMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit test: no Spring, no database — just `new OrderStateMachine()`.
 * Runs in milliseconds.
 *
 * @ParameterizedTest runs the same test once per row of @CsvSource,
 * so one method checks many transitions.
 */
class OrderStateMachineTest {

    private final OrderStateMachine fsm = new OrderStateMachine();

    @ParameterizedTest(name = "{0} → {1} is allowed")
    @CsvSource({
            "PLACED, ACCEPTED", "ACCEPTED, PREPARING", "PREPARING, READY",
            "READY, PICKED_UP", "PICKED_UP, DELIVERED",
            "PLACED, CANCELLED", "ACCEPTED, CANCELLED", "PREPARING, CANCELLED"
    })
    void allowedTransitions(OrderStatus from, OrderStatus to) {
        assertThat(fsm.canTransition(from, to)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} is rejected")
    @CsvSource({
            "PLACED, READY",          // can't skip the kitchen
            "PLACED, DELIVERED",
            "ACCEPTED, PLACED",       // no going backwards
            "READY, CANCELLED",       // food is cooked, a rider may be on the way
            "PICKED_UP, CANCELLED",
            "PREPARING, PICKED_UP"
    })
    void rejectedTransitions(OrderStatus from, OrderStatus to) {
        assertThat(fsm.canTransition(from, to)).isFalse();
        assertThatThrownBy(() -> fsm.validateTransition(from, to))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid order status transition");
    }

    @DisplayName("DELIVERED and CANCELLED are final — nothing can follow them")
    @ParameterizedTest(name = "{0} → anything is rejected")
    @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "CANCELLED"})
    void terminalStatesHaveNoExit(OrderStatus terminal) {
        for (OrderStatus next : OrderStatus.values()) {
            assertThat(fsm.canTransition(terminal, next)).isFalse();
        }
    }

    @Test
    void errorMessageListsWhatIsAllowed() {
        assertThatThrownBy(() -> fsm.validateTransition(OrderStatus.PLACED, OrderStatus.READY))
                .hasMessageContaining("ACCEPTED");
    }
}
