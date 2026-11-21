package com.fooddelivery.backend.entity;

/**
 * Lifecycle of one delivery assignment (one partner ↔ one order).
 *
 *   ASSIGNED ──pickup──► PICKED_UP ──deliver──► DELIVERED
 *      │
 *      └──partner rejects──► CANCELLED   (order gets reassigned to someone else)
 *
 * ASSIGNED and PICKED_UP are "active" — the partner is busy with this order.
 */
public enum DeliveryAssignmentStatus {
    ASSIGNED,
    PICKED_UP,
    DELIVERED,
    CANCELLED;

    public static final java.util.Set<DeliveryAssignmentStatus> ACTIVE =
            java.util.EnumSet.of(ASSIGNED, PICKED_UP);
}
