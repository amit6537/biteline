package com.fooddelivery.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Order entity — central transactional entity in the system.
 *
 * CONCEPT: Aggregate Root & Cascading
 * ────────────────────────────────────
 * In Domain-Driven Design (DDD), Order is an Aggregate Root.
 * Order items only exist in the context of an Order.
 *
 * cascade = CascadeType.ALL on orderItems means when we save the Order,
 * Hibernate automatically persists all child OrderItem entities.
 * orphanRemoval = true ensures deleting an item from the collection removes it from DB.
 */
@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_address_id", nullable = false)
    private Address deliveryAddress;

    @Column(name = "delivery_address_text", nullable = false, columnDefinition = "TEXT")
    private String deliveryAddressText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private OrderStatus status = OrderStatus.PLACED;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal deliveryFee = new BigDecimal("30.00");

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "special_instructions", columnDefinition = "TEXT")
    private String specialInstructions;

    @CreatedDate
    @Column(name = "placed_at", nullable = false, updatable = false)
    private LocalDateTime placedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "estimated_delivery_at")
    private LocalDateTime estimatedDeliveryAt;

    // Phase 12: when the ETA was last recalculated (throttles GPS-driven updates)
    @Column(name = "eta_updated_at")
    private LocalDateTime etaUpdatedAt;

    // Phase 12: snapshot of the drop location (null if the address had no coordinates)
    @Column(name = "delivery_lat")
    private Double deliveryLat;

    @Column(name = "delivery_lng")
    private Double deliveryLng;

    // Ops alert "nobody is delivering this order" already sent? (send once)
    @Column(name = "assignment_alerted_at")
    private LocalDateTime assignmentAlertedAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude // Order <-> OrderItem point at each other; including both would recurse forever
    private List<OrderItem> items = new ArrayList<>();
}

