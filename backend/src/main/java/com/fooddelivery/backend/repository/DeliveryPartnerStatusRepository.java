package com.fooddelivery.backend.repository;

import com.fooddelivery.backend.entity.DeliveryPartnerStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryPartnerStatusRepository extends JpaRepository<DeliveryPartnerStatus, UUID> {

    /**
     * Load a partner's status row AND lock it, so a status update from the app
     * can't overwrite a change the assignment logic made at the same moment
     * (the "lost update" problem).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DeliveryPartnerStatus s WHERE s.partnerId = :id")
    Optional<DeliveryPartnerStatus> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Finds the NEAREST free delivery partner to a point (the restaurant)
     * and LOCKS that partner's row.
     *
     * CONCEPT: Haversine formula
     * ───────────────────────────
     * The Earth is a sphere, so distance between two lat/lng points isn't a
     * straight line. Haversine (great-circle distance) gives the km:
     *   6371 * acos( cos(lat1)·cos(lat2)·cos(lng2−lng1) + sin(lat1)·sin(lat2) )
     *   (6371 = Earth's radius in km; LEAST/GREATEST keep acos() input in
     *   [-1, 1] — tiny rounding errors can otherwise make it 1.0000001 → error)
     *
     * Rules a partner must pass:
     *   - is_available = true and has a location
     *   - pinged us recently (last_seen_at > :seenAfter) — ignores dead phones
     *   - is an active user with role DELIVERY_PARTNER
     *   - is not already on an active delivery (double safety)
     *   - has NOT been on this order before — unless they REJECTED it more than
     *     the cooldown ago (then they may be asked again; otherwise an order
     *     where everyone said "no" once would be stuck forever)
     *   - is within :maxKm of the restaurant
     *
     * CONCEPT: FOR UPDATE SKIP LOCKED
     * ───────────────────────────────
     * Two orders become READY at the same moment, both want the same nearest
     * partner. FOR UPDATE locks the chosen partner row; SKIP LOCKED makes the
     * other request skip that locked row and take the NEXT nearest partner
     * instead of waiting. → no partner is ever given two orders at once.
     *
     * nativeQuery = true because JPQL has no acos/radians/SKIP LOCKED.
     */
    @Query(nativeQuery = true, value = """
            SELECT s.*
            FROM delivery_partner_status s
            JOIN users u ON u.id = s.partner_id
            WHERE s.is_available = TRUE
              AND s.current_lat IS NOT NULL
              AND s.current_lng IS NOT NULL
              AND s.last_seen_at > :seenAfter
              AND u.is_active = TRUE
              AND u.role = 'DELIVERY_PARTNER'
              AND NOT EXISTS (SELECT 1 FROM delivery_assignments a
                              WHERE a.partner_id = s.partner_id
                                AND a.status IN ('ASSIGNED', 'PICKED_UP'))
              AND NOT EXISTS (SELECT 1 FROM delivery_assignments a
                              WHERE a.partner_id = s.partner_id
                                AND a.order_id = :orderId
                                AND (a.status <> 'CANCELLED' OR a.cancelled_at > :rejectCooldownSince))
              AND (6371 * acos(LEAST(1.0, GREATEST(-1.0,
                     cos(radians(:lat)) * cos(radians(s.current_lat))
                       * cos(radians(s.current_lng) - radians(:lng))
                   + sin(radians(:lat)) * sin(radians(s.current_lat)))))) <= :maxKm
            ORDER BY (6371 * acos(LEAST(1.0, GREATEST(-1.0,
                     cos(radians(:lat)) * cos(radians(s.current_lat))
                       * cos(radians(s.current_lng) - radians(:lng))
                   + sin(radians(:lat)) * sin(radians(s.current_lat))))))
            LIMIT 1
            FOR UPDATE OF s SKIP LOCKED
            """)
    Optional<DeliveryPartnerStatus> findNearestAvailablePartner(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("maxKm") double maxKm,
            @Param("seenAfter") LocalDateTime seenAfter,
            @Param("orderId") UUID orderId,
            @Param("rejectCooldownSince") LocalDateTime rejectCooldownSince);
}
