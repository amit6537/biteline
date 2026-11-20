package com.fooddelivery.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Address entity — maps to the "addresses" table in PostgreSQL.
 *
 * CONCEPT: User Addresses & Defensive Snapshotting
 * ─────────────────────────────────────────────────
 * Users can store multiple delivery addresses (Home, Office, etc.).
 * When an order is placed, we associate the order with the address ID,
 * BUT we ALSO snapshot the formatted address text into the Order entity.
 *
 * Why? If a user updates their "Home" address next month, past orders
 * must still accurately reflect where the food was originally delivered!
 */
@Entity
@Table(name = "addresses")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String label = "Home"; // e.g. "Home", "Work", "Other"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String street;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(nullable = false, length = 10)
    private String pincode;

    @Column(precision = 9, scale = 6)
    private Double latitude;

    @Column(precision = 9, scale = 6)
    private Double longitude;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Helper method to generate a clean, snapshot-friendly formatted string.
     */
    public String toFormattedAddress() {
        return String.format("%s, %s, %s - %s", street, city, state, pincode);
    }
}

