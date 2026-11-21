package com.fooddelivery.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DeliveryAssignment — "partner X is delivering order Y".
 *
 * WHY a separate table instead of a partner_id column on orders?
 *   If a partner rejects, we mark this row CANCELLED and create a NEW row
 *   for the next partner. So we keep the full history of who was asked.
 *
 * WHY @Getter/@Setter instead of @Data here?
 *   @Data also generates equals/hashCode/toString over ALL fields, including
 *   the lazy `order` and `partner` relations. That can trigger extra DB
 *   queries or infinite loops (we hit exactly that with Order ↔ OrderItem).
 *   For entities, plain getters/setters are the safer habit.
 */
@Entity
@Table(name = "delivery_assignments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private User partner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DeliveryAssignmentStatus status = DeliveryAssignmentStatus.ASSIGNED;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // When it was rejected/released — drives the "ask again after cooldown" rule
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
}
