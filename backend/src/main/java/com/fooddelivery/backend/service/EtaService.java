package com.fooddelivery.backend.service;

import com.fooddelivery.backend.entity.*;
import com.fooddelivery.backend.repository.DeliveryAssignmentRepository;
import com.fooddelivery.backend.repository.DeliveryPartnerStatusRepository;
import com.fooddelivery.backend.repository.OrderRepository;
import com.fooddelivery.backend.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * EtaService — Phase 12: "when will my food arrive?"
 *
 * ETA = the time still needed for each step that hasn't happened yet:
 *
 *   PLACED / ACCEPTED / PREPARING:
 *       remaining kitchen time + finding a partner + restaurant → customer
 *   READY (partner assigned):
 *       partner → restaurant + restaurant → customer
 *   READY (no partner yet):
 *       finding a partner + restaurant → customer
 *   PICKED_UP:
 *       partner's live position → customer
 *
 * travel time = distance × roadFactor ÷ avgSpeed   (see GeoUtils)
 *
 * The ETA is recalculated on every status change, on assignment, and when
 * the partner's GPS moves (throttled: GPS pings every few seconds, we don't
 * need a DB write every few seconds).
 *
 * Edge cases:
 *   - customer address has no coordinates → use a default travel time
 *   - partner's GPS is stale (phone off)  → don't trust it, fall back
 *   - DELIVERED / CANCELLED               → ETA is frozen, never changed
 *   - ETA never goes below 1 minute
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EtaService {

    private final OrderRepository orderRepository;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryPartnerStatusRepository partnerStatusRepository;

    @Value("${app.eta.avg-speed-kmh:20}")
    private double avgSpeedKmh;

    @Value("${app.eta.road-factor:1.3}")
    private double roadFactor;

    @Value("${app.eta.partner-search-minutes:5}")
    private long partnerSearchMinutes;

    @Value("${app.eta.default-travel-minutes:15}")
    private long defaultTravelMinutes;

    @Value("${app.eta.min-refresh-seconds:30}")
    private long minRefreshSeconds;

    @Value("${app.delivery.partner-stale-minutes:10}")
    private long partnerStaleMinutes;

    /** Recalculate and store the ETA. Caller must hold the order (in a transaction). */
    public void refresh(Order order) {
        minutesRemaining(order).ifPresent(minutes -> {
            LocalDateTime now = LocalDateTime.now();
            order.setEstimatedDeliveryAt(now.plusMinutes(minutes));
            order.setEtaUpdatedAt(now);
        });
    }

    /**
     * Partner's GPS moved → refresh the ETA of the order they're carrying.
     * Called AFTER the partner-status transaction commits, in its OWN
     * transaction (REQUIRES_NEW), so it never holds the partner row and the
     * order row at the same time in the wrong order (no deadlocks).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshForPartner(UUID partnerId) {
        assignmentRepository.findFirstByPartnerIdAndStatusIn(partnerId, DeliveryAssignmentStatus.ACTIVE)
                .ifPresent(a -> orderRepository.findByIdForUpdate(a.getOrder().getId()).ifPresent(order -> {
                    LocalDateTime last = order.getEtaUpdatedAt();
                    if (last != null && last.isAfter(LocalDateTime.now().minusSeconds(minRefreshSeconds))) {
                        return; // throttle: updated very recently
                    }
                    refresh(order);
                }));
    }

    /** Minutes until delivery, or empty if the ETA must not change. */
    public Optional<Long> minutesRemaining(Order order) {
        Restaurant r = order.getRestaurant();
        long restaurantToCustomer = travelOrDefault(r.getLatitude(), r.getLongitude(),
                order.getDeliveryLat(), order.getDeliveryLng());
        long minutes = switch (order.getStatus()) {
            case PLACED -> r.getAvgPrepMinutes() + partnerSearchMinutes + restaurantToCustomer;
            case ACCEPTED, PREPARING -> remainingPrep(order) + partnerSearchMinutes + restaurantToCustomer;
            case READY -> livePartnerLocation(order.getId())
                    .map(p -> travel(p.getCurrentLat(), p.getCurrentLng(), r.getLatitude(), r.getLongitude()))
                    .orElse(partnerSearchMinutes) + restaurantToCustomer;
            case PICKED_UP -> {
                Optional<DeliveryPartnerStatus> partner = livePartnerLocation(order.getId());
                if (partner.isEmpty() || order.getDeliveryLat() == null) {
                    yield -1; // nothing better to go on → keep the current ETA
                }
                DeliveryPartnerStatus p = partner.get();
                yield travel(p.getCurrentLat(), p.getCurrentLng(), order.getDeliveryLat(), order.getDeliveryLng());
            }
            case DELIVERED, CANCELLED -> -1;
        };
        return minutes < 0 ? Optional.empty() : Optional.of(Math.max(1, minutes));
    }

    /**
     * The assigned partner's position — only if fresh. A location from a
     * phone that went silent 20 minutes ago would make the ETA a lie.
     */
    public Optional<DeliveryPartnerStatus> livePartnerLocation(UUID orderId) {
        return assignmentRepository.findFirstByOrderIdAndStatusIn(orderId, DeliveryAssignmentStatus.ACTIVE)
                .flatMap(a -> partnerStatusRepository.findById(a.getPartner().getId()))
                .filter(s -> s.getCurrentLat() != null && s.getCurrentLng() != null)
                .filter(s -> !isStale(s));
    }

    public boolean isStale(DeliveryPartnerStatus s) {
        return s.getLastSeenAt() == null
                || s.getLastSeenAt().isBefore(LocalDateTime.now().minusMinutes(partnerStaleMinutes));
    }

    // ── small helpers ────────────────────────────────────────

    /** Kitchen time left = prep time − minutes since the restaurant accepted (never below 0). */
    private long remainingPrep(Order order) {
        long prep = order.getRestaurant().getAvgPrepMinutes();
        if (order.getAcceptedAt() == null) return prep;
        long elapsed = Duration.between(order.getAcceptedAt(), LocalDateTime.now()).toMinutes();
        return Math.max(0, prep - elapsed);
    }

    private long travel(double lat1, double lng1, double lat2, double lng2) {
        return GeoUtils.travelMinutes(GeoUtils.distanceKm(lat1, lng1, lat2, lng2), avgSpeedKmh, roadFactor);
    }

    /** Travel time if we know both points, otherwise a sensible default. */
    private long travelOrDefault(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return defaultTravelMinutes;
        }
        return travel(lat1, lng1, lat2, lng2);
    }
}
