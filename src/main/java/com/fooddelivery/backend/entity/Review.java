package com.fooddelivery.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Review — one per DELIVERED order (UNIQUE order_id in the DB).
 *
 * foodRating     → goes into the restaurant's average
 * deliveryRating → optional, goes into the delivery partner's average
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    // Lazy relation only for showing the reviewer's name on the restaurant page
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "partner_id")
    private UUID partnerId;

    @Column(name = "food_rating", nullable = false)
    private int foodRating;

    @Column(name = "delivery_rating")
    private Integer deliveryRating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
