package com.fooddelivery.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Restaurant entity — maps to the "restaurants" table.
 *
 * CONCEPT: @ManyToOne vs storing just the ownerId
 * ───────────────────────────────────────────────────
 * @ManyToOne creates a foreign-key relationship at the ORM level.
 * JPA can then lazy-load the owner User when needed.
 *
 * fetch = FetchType.LAZY (default for @ManyToOne in Hibernate):
 *   Owner User is NOT loaded until you call restaurant.getOwner().
 *   This avoids unnecessary JOINs when you just need restaurant data.
 *
 * @JoinColumn(name = "owner_id") maps to the owner_id FK column.
 *
 * CONCEPT: avg_rating and total_reviews
 * ──────────────────────────────────────
 * We store the pre-computed average instead of computing it via
 * SELECT AVG(rating) on every restaurant fetch.
 * When a new review is added (Phase 11), we update these two fields.
 * This is a classic "denormalization for read performance" trade-off.
 */
@Entity
@Table(name = "restaurants")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cuisine_type", length = 100)
    private String cuisineType;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "avg_rating", nullable = false)
    @Builder.Default
    private Double avgRating = 0.0;

    @Column(name = "total_reviews", nullable = false)
    @Builder.Default
    private Integer totalReviews = 0;

    @Column(name = "is_open", nullable = false)
    @Builder.Default
    private boolean isOpen = true;

    @Column(name = "delivery_radius_km", nullable = false)
    @Builder.Default
    private Double deliveryRadiusKm = 5.0;

    @Column(name = "min_order_amount", nullable = false)
    @Builder.Default
    private Double minOrderAmount = 0.0;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false, length = 100)
    private String city;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

