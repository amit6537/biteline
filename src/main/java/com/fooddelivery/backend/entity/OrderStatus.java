package com.fooddelivery.backend.entity;

/**
 * OrderStatus enum — defines the states of an order in the system.
 *
 * State Transitions (Order State Machine — Phase 5):
 *   PLACED ──> ACCEPTED ──> PREPARING ──> READY ──> PICKED_UP ──> DELIVERED
 *     │           │            │           │
 *     └───> CANCELLED <────────┴───────────┘
 */
public enum OrderStatus {
    PLACED,
    ACCEPTED,
    PREPARING,
    READY,
    PICKED_UP,
    DELIVERED,
    CANCELLED
}

