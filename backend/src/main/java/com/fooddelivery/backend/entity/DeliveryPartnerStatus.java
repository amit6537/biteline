package com.fooddelivery.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DeliveryPartnerStatus — a partner's live state: free or not, and where.
 *
 * One row per partner (partner_id is the primary key, not a generated id).
 * The row is created the first time the partner goes online.
 *
 * online    = the partner's own choice ("I want orders" / "I'm done today").
 * available = what assignment uses: online AND not busy right now.
 *             false while delivering; after delivery it goes back to `online`.
 *
 * lastSeenAt = last time the app pinged us. A partner whose phone died
 * 2 hours ago still says isAvailable=true — lastSeenAt lets us ignore them.
 */
@Entity
@Table(name = "delivery_partner_status")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPartnerStatus {

    @Id
    @Column(name = "partner_id")
    private UUID partnerId;

    @Column(name = "is_online", nullable = false)
    private boolean online;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    // Plain Double, no precision/scale (Hibernate 7 rejects scale on floats)
    @Column(name = "current_lat")
    private Double currentLat;

    @Column(name = "current_lng")
    private Double currentLng;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}
