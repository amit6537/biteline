package com.fooddelivery.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * MenuItem entity — maps to the "menu_items" table.
 *
 * CONCEPT: BigDecimal for Money
 * ──────────────────────────────
 * NEVER use float or double for money.
 * float/double use binary floating-point which cannot represent
 * decimal fractions exactly:
 *   0.1 + 0.2 = 0.30000000000000004 (in double)
 *
 * BigDecimal uses exact decimal arithmetic:
 *   new BigDecimal("0.1").add(new BigDecimal("0.2")) = 0.3 exactly
 *
 * For price, use BigDecimal with scale 2 (2 decimal places).
 * @Column(precision=10, scale=2) → up to 99999999.99 in DB.
 */
@Entity
@Table(name = "menu_items")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String category = "Main Course";

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private boolean isAvailable = true;

    @Column(name = "is_vegetarian", nullable = false)
    @Builder.Default
    private boolean isVegetarian = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

