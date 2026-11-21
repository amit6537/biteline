package com.fooddelivery.backend.statemachine;

import com.fooddelivery.backend.entity.OrderStatus;
import com.fooddelivery.backend.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * OrderStateMachine — Defines and enforces valid state transitions for an order.
 *
 * CONCEPT: Finite State Machine (FSM)
 * ────────────────────────────────────
 * A Finite State Machine ensures that an entity can only transition from
 * state A to state B if such a transition is explicitly allowed.
 *
 * Valid Happy Path:
 *   PLACED ──> ACCEPTED ──> PREPARING ──> READY ──> PICKED_UP ──> DELIVERED
 *
 * Cancellation Rules:
 *   - Orders can be CANCELLED while PLACED, ACCEPTED, or PREPARING.
 *   - Once an order is READY or PICKED_UP, it cannot be simply cancelled by the customer.
 *   - DELIVERED and CANCELLED are terminal states (no transitions allowed).
 */
@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            OrderStatus.PLACED, EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.CANCELLED),
            OrderStatus.ACCEPTED, EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED),
            OrderStatus.PREPARING, EnumSet.of(OrderStatus.READY, OrderStatus.CANCELLED),
            OrderStatus.READY, EnumSet.of(OrderStatus.PICKED_UP),
            OrderStatus.PICKED_UP, EnumSet.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class)
    );

    /**
     * Checks if a transition from currentStatus to targetStatus is valid.
     */
    public boolean canTransition(OrderStatus currentStatus, OrderStatus targetStatus) {
        Set<OrderStatus> allowedNext = VALID_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(OrderStatus.class));
        return allowedNext.contains(targetStatus);
    }

    /**
     * Validates transition; throws BadRequestException if invalid.
     */
    public void validateTransition(OrderStatus currentStatus, OrderStatus targetStatus) {
        if (!canTransition(currentStatus, targetStatus)) {
            throw new BadRequestException(String.format(
                    "Invalid order status transition from '%s' to '%s'. Allowed next states: %s",
                    currentStatus, targetStatus, VALID_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(OrderStatus.class))
            ));
        }
    }
}

