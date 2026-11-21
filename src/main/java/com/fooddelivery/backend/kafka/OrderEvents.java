package com.fooddelivery.backend.kafka;

import com.fooddelivery.backend.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka Event Records — immutable data classes for each event type.
 *
 * CONCEPT: Event-Driven Architecture
 * ─────────────────────────────────────
 * Instead of calling other services directly (tight coupling),
 * we publish events to Kafka topics. Interested services consume those events.
 *
 * Direct call (tight coupling):
 *   OrderService → DeliveryService.assign(order)
 *   Problem: If DeliveryService is down → OrderService fails too.
 *
 * Event-driven (loose coupling):
 *   OrderService → Kafka topic "order.ready"
 *   DeliveryService consumes "order.ready" when it comes back up.
 *   Problem goes away: services are independent.
 *
 * CONCEPT: Java Records
 * ──────────────────────
 * Java 16+ records are immutable data holders — perfect for events.
 * No setters, no mutability — an event is a fact about the past.
 * "Order 123 was placed at 10:00" cannot be changed.
 *
 * We group all events in one file as nested records for clarity.
 */
public class OrderEvents {

    /**
     * Published when a customer places a new order.
     * Topic: order.placed
     * Consumer: PaymentService (to initiate payment link)
     */
    public record OrderPlacedEvent(
            UUID orderId,
            UUID customerId,
            String customerName,
            String customerEmail,
            UUID restaurantId,
            String restaurantName,
            BigDecimal totalAmount,
            LocalDateTime placedAt
    ) {}

    /**
     * Published when a payment is confirmed successfully.
     * Topic: payment.success
     * Consumers: OrderService (triggers ACCEPTED flow), NotificationService
     */
    public record PaymentSuccessEvent(
            UUID paymentId,
            UUID orderId,
            UUID customerId,
            String customerEmail,
            BigDecimal amount,
            String transactionId,
            LocalDateTime paidAt
    ) {}

    /**
     * Published on EVERY order status change.
     * Topic: order.status.changed
     * Consumer: NotificationService (sends push/email at each step)
     */
    public record OrderStatusChangedEvent(
            UUID orderId,
            UUID customerId,
            String customerEmail,
            UUID restaurantId,
            String restaurantName,
            OrderStatus previousStatus,
            OrderStatus newStatus,
            LocalDateTime changedAt
    ) {}

    /**
     * Published when restaurant marks an order READY for pickup.
     * Topic: order.ready
     * Consumer: DeliveryService (assigns nearest available partner)
     */
    public record OrderReadyEvent(
            UUID orderId,
            UUID restaurantId,
            String restaurantName,
            double restaurantLat,
            double restaurantLng,
            BigDecimal totalAmount,
            LocalDateTime readyAt
    ) {}

    /**
     * Published when delivery partner marks order DELIVERED.
     * Topic: order.delivered
     * Consumer: NotificationService (completion message + review prompt)
     */
    public record OrderDeliveredEvent(
            UUID orderId,
            UUID customerId,
            String customerEmail,
            UUID partnerId,
            LocalDateTime deliveredAt
    ) {}

    /**
     * Published when a delivery partner is assigned to an order (Phase 8).
     * Consumed by: NotificationConsumer → "Rahul is picking up your order".
     */
    /**
     * An order has been READY for too long and no partner took it.
     * Consumed by: NotificationConsumer → customer ("slight delay") + all admins.
     */
    public record DeliveryDelayedEvent(
            UUID orderId,
            UUID customerId,
            UUID restaurantId,
            String restaurantName,
            long waitingMinutes,
            LocalDateTime detectedAt
    ) {}

    /**
     * A customer reviewed an order (Phase 11).
     * Consumed by: NotificationConsumer → restaurant owner (+ partner if rated).
     */
    public record ReviewCreatedEvent(
            UUID reviewId,
            UUID orderId,
            UUID restaurantId,
            UUID partnerId,
            int foodRating,
            Integer deliveryRating,
            LocalDateTime createdAt
    ) {}

    public record DeliveryAssignedEvent(
            UUID assignmentId,
            UUID orderId,
            UUID customerId,
            String customerEmail,
            UUID partnerId,
            String partnerName,
            LocalDateTime assignedAt
    ) {}
}
