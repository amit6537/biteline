package com.fooddelivery.backend.service;

import com.fooddelivery.backend.dto.request.UpdatePartnerStatusRequest;
import com.fooddelivery.backend.dto.response.DeliveryAssignmentResponse;
import com.fooddelivery.backend.dto.response.PartnerStatusResponse;
import com.fooddelivery.backend.entity.*;
import com.fooddelivery.backend.exception.BadRequestException;
import com.fooddelivery.backend.exception.ForbiddenException;
import com.fooddelivery.backend.exception.ResourceNotFoundException;
import com.fooddelivery.backend.kafka.OrderEventProducer;
import com.fooddelivery.backend.kafka.OrderEvents;
import com.fooddelivery.backend.repository.DeliveryAssignmentRepository;
import com.fooddelivery.backend.repository.DeliveryPartnerStatusRepository;
import com.fooddelivery.backend.repository.OrderRepository;
import com.fooddelivery.backend.repository.UserRepository;
import com.fooddelivery.backend.util.PageUtils;
import com.fooddelivery.backend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * DeliveryService — Phase 8: who delivers which order.
 *
 * Three jobs:
 *   1. Partner self-service: go online/offline, send location, see jobs.
 *   2. Assignment: when an order is READY, pick the nearest free partner.
 *   3. Guard rails for OrderService: only the ASSIGNED partner may pick up /
 *      deliver an order; on delivery the partner becomes free again.
 *
 * FLOW (happy path):
 *   owner marks READY → OrderService publishes order.ready (after commit)
 *     → DeliveryEventConsumer → assignPartner(orderId)
 *       → nearest free partner gets an ASSIGNED row, becomes unavailable
 *       → publishes delivery.assigned → NotificationConsumer tells customer
 *   partner: PATCH /orders/{id}/pickup → assignment PICKED_UP
 *   partner: PATCH /orders/{id}/deliver → assignment DELIVERED, partner free
 *
 * If nobody is free, DeliveryAssignmentScheduler retries every 30s.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryPartnerStatusRepository partnerStatusRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final OrderEventProducer eventProducer;
    private final EtaService etaService;

    /** Phase 12 geofence: must be this close to pick up / drop off. */
    @Value("${app.delivery.geofence.enabled:true}")
    private boolean geofenceEnabled;

    @Value("${app.delivery.geofence.pickup-radius-km:0.5}")
    private double pickupRadiusKm;

    @Value("${app.delivery.geofence.dropoff-radius-km:0.5}")
    private double dropoffRadiusKm;

    /** Only partners within this distance of the restaurant are considered. */
    @Value("${app.delivery.max-radius-km:10}")
    private double maxRadiusKm;

    /** A partner who hasn't pinged for this long is treated as offline. */
    @Value("${app.delivery.partner-stale-minutes:10}")
    private long partnerStaleMinutes;

    /** A partner who rejected an order may be offered it again after this long. */
    @Value("${app.delivery.reject-cooldown-minutes:5}")
    private long rejectCooldownMinutes;

    // ═════════════════════════════════════════════════════════
    // 1. PARTNER SELF-SERVICE
    // ═════════════════════════════════════════════════════════

    /**
     * Partner app calls this to go online/offline and to report location.
     * Creates the status row on the very first call ("upsert").
     */
    @Transactional
    public PartnerStatusResponse updateMyStatus(UpdatePartnerStatusRequest request) {
        User partner = securityUtils.getCurrentUser();

        // Lock the row FIRST, then read everything else. Otherwise an assignment
        // happening in parallel could be overwritten by our stale copy.
        DeliveryPartnerStatus status = partnerStatusRepository.findByIdForUpdate(partner.getId())
                .orElseGet(() -> DeliveryPartnerStatus.builder()
                        .partnerId(partner.getId())
                        .online(false)
                        .available(false)
                        .build());
        Optional<DeliveryAssignment> active = findActiveAssignmentOfPartner(partner.getId());

        if (request.getAvailable() != null) {
            // `available` in the request = the partner's WISH (online/offline).
            // Actually free for a new order = online AND not on a delivery now.
            // Going offline mid-delivery is fine: they finish this one, then stop.
            status.setOnline(request.getAvailable());
            status.setAvailable(request.getAvailable() && active.isEmpty());
        }
        status.setCurrentLat(request.getLatitude());
        status.setCurrentLng(request.getLongitude());
        status.setLastSeenAt(LocalDateTime.now()); // "heartbeat"

        partnerStatusRepository.save(status);

        // Phase 12: rider moved → their order's ETA may change. Done AFTER this
        // commit, in a separate transaction (see EtaService.refreshForPartner).
        if (active.isPresent()) {
            UUID partnerId = partner.getId();
            afterCommit(() -> {
                try {
                    etaService.refreshForPartner(partnerId);
                } catch (RuntimeException e) {
                    log.warn("ETA refresh failed for partner {}: {}", partnerId, e.getMessage());
                }
            });
        }
        log.info("Partner {} status: available={} at ({}, {})",
                partner.getEmail(), status.isAvailable(), status.getCurrentLat(), status.getCurrentLng());
        return toStatusResponse(status, active.orElse(null));
    }

    @Transactional(readOnly = true)
    public PartnerStatusResponse getMyStatus() {
        UUID partnerId = securityUtils.getCurrentUserId();
        DeliveryPartnerStatus status = partnerStatusRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "You have not gone online yet. Call PUT /api/delivery/status first."));
        return toStatusResponse(status, findActiveAssignmentOfPartner(partnerId).orElse(null));
    }

    /** The partner's current job, if any. */
    @Transactional(readOnly = true)
    public Optional<DeliveryAssignmentResponse> getMyCurrentAssignment() {
        UUID partnerId = securityUtils.getCurrentUserId();
        return findActiveAssignmentOfPartner(partnerId).map(this::toAssignmentResponse);
    }

    /** The partner's history: all jobs, newest first. */
    @Transactional(readOnly = true)
    public Page<DeliveryAssignmentResponse> getMyAssignments(int page, int size) {
        UUID partnerId = securityUtils.getCurrentUserId();
        return assignmentRepository
                .findByPartnerIdOrderByAssignedAtDesc(partnerId, PageUtils.of(page, size))
                .map(this::toAssignmentResponse);
    }

    /**
     * Partner says "no" to a job they haven't picked up yet.
     * → their assignment becomes CANCELLED (kept as history)
     * → they become free again
     * → we immediately try the NEXT nearest partner (the query skips anyone
     *   who was already assigned this order, so it won't bounce back to them).
     */
    @Transactional
    public Optional<DeliveryAssignmentResponse> rejectAssignment(UUID assignmentId) {
        UUID partnerId = securityUtils.getCurrentUserId();

        // 1. Only the order id first (no entity loaded yet)...
        UUID orderId = assignmentRepository.findOrderIdByIdAndPartnerId(assignmentId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));
        // 2. ...lock the order (same lock order as everywhere: order → partner)...
        Order order = lockOrder(orderId);
        // 3. ...and only NOW load the assignment, so we see its latest state
        //    (e.g. if a "pickup" request finished a millisecond ago).
        DeliveryAssignment assignment = assignmentRepository.findById(assignmentId).orElseThrow();

        if (assignment.getStatus() != DeliveryAssignmentStatus.ASSIGNED) {
            throw new BadRequestException("Only an assignment that is not yet picked up can be rejected.");
        }

        assignment.setStatus(DeliveryAssignmentStatus.CANCELLED);
        assignment.setCancelledAt(LocalDateTime.now()); // starts the reject cooldown
        assignmentRepository.saveAndFlush(assignment); // flush: the next query must see CANCELLED
        freePartner(partnerId);
        log.info("Partner {} rejected order {}. Reassigning...", partnerId, order.getId());

        return tryAssign(order).map(this::toAssignmentResponse);
    }

    /**
     * Scheduler calls this for an ASSIGNED job whose partner went silent
     * (phone died before pickup). Cancel it and give the order to someone else.
     * Re-checks everything under the lock — the partner may have come back.
     */
    @Transactional
    public void releaseStaleAssignment(UUID assignmentId) {
        // Same pattern as reject: id → lock → THEN load (never load before the lock,
        // or Hibernate hands us its cached, possibly outdated copy afterwards)
        UUID orderId = assignmentRepository.findOrderIdById(assignmentId).orElse(null);
        if (orderId == null) return;
        Order order = lockOrder(orderId);

        DeliveryAssignment assignment = assignmentRepository.findById(assignmentId).orElseThrow();
        UUID partnerId = assignment.getPartner().getId();
        DeliveryPartnerStatus partner = partnerStatusRepository.findByIdForUpdate(partnerId).orElse(null);

        boolean stillStale = partner != null && partner.getLastSeenAt()
                .isBefore(LocalDateTime.now().minusMinutes(partnerStaleMinutes));
        if (assignment.getStatus() != DeliveryAssignmentStatus.ASSIGNED || !stillStale) {
            return; // picked up meanwhile, or partner pinged again — leave it
        }

        assignment.setStatus(DeliveryAssignmentStatus.CANCELLED);
        assignment.setCancelledAt(LocalDateTime.now());
        assignmentRepository.saveAndFlush(assignment);
        // They're unreachable: treat as offline until they open the app again
        partner.setOnline(false);
        partner.setAvailable(false);
        log.warn("[DELIVERY] Partner {} went silent before pickup. Order {} released for reassignment.",
                partnerId, order.getId());

        tryAssign(order);
    }

    // ═════════════════════════════════════════════════════════
    // 2. ASSIGNMENT
    // ═════════════════════════════════════════════════════════

    /**
     * Assign the nearest free partner to a READY order.
     * Called by the Kafka consumer (instantly) and the scheduler (retries).
     *
     * Safe to call many times for the same order ("idempotent"):
     * if the order already has an active assignment, nothing new happens.
     *
     * @return the assignment, or empty if nobody is free right now
     */
    @Transactional
    public Optional<DeliveryAssignment> assignPartner(UUID orderId) {
        Order order = lockOrder(orderId);
        return tryAssign(order);
    }

    /** Assumes the caller already holds the order's row lock. */
    private Optional<DeliveryAssignment> tryAssign(Order order) {
        if (order.getStatus() != OrderStatus.READY) {
            log.debug("Order {} is {}, not READY. No assignment needed.", order.getId(), order.getStatus());
            return Optional.empty();
        }

        Optional<DeliveryAssignment> existing = assignmentRepository
                .findFirstByOrderIdAndStatusIn(order.getId(), DeliveryAssignmentStatus.ACTIVE);
        if (existing.isPresent()) {
            return existing; // already assigned — duplicate event or scheduler overlap
        }

        Restaurant restaurant = order.getRestaurant();
        Optional<DeliveryPartnerStatus> nearest = partnerStatusRepository.findNearestAvailablePartner(
                restaurant.getLatitude(),
                restaurant.getLongitude(),
                maxRadiusKm,
                LocalDateTime.now().minusMinutes(partnerStaleMinutes),
                order.getId(),
                LocalDateTime.now().minusMinutes(rejectCooldownMinutes));

        if (nearest.isEmpty()) {
            log.warn("[DELIVERY] No free partner within {} km of '{}' for order {}. Will retry.",
                    maxRadiusKm, restaurant.getName(), order.getId());
            return Optional.empty();
        }

        return Optional.of(createAssignment(order, nearest.get()));
    }

    /** Shared by automatic and manual (admin) assignment. */
    private DeliveryAssignment createAssignment(Order order, DeliveryPartnerStatus partnerStatus) {
        partnerStatus.setAvailable(false); // busy now — no second order for them

        User partner = userRepository.getReferenceById(partnerStatus.getPartnerId());
        DeliveryAssignment assignment = assignmentRepository.save(DeliveryAssignment.builder()
                .order(order)
                .partner(partner)
                .status(DeliveryAssignmentStatus.ASSIGNED)
                .assignedAt(LocalDateTime.now())
                .build());

        etaService.refresh(order); // ETA now uses the partner's real distance
        log.info("[DELIVERY] ✅ Order {} assigned to partner {} ({})",
                order.getId(), partner.getName(), partner.getId());

        eventProducer.publishDeliveryAssigned(new OrderEvents.DeliveryAssignedEvent(
                assignment.getId(),
                order.getId(),
                order.getCustomer().getId(),
                order.getCustomer().getEmail(),
                partner.getId(),
                partner.getName(),
                assignment.getAssignedAt()));

        return assignment;
    }

    // ═════════════════════════════════════════════════════════
    // 2b. STUCK ORDERS — ops alert + manual assignment
    // ═════════════════════════════════════════════════════════

    /**
     * Called by the scheduler for an order that has been READY too long with
     * no partner. Re-checks under the lock, marks it as alerted (so we alert
     * only ONCE), logs an ERROR and publishes delivery.delayed → the customer
     * and every admin get a notification (+ email).
     */
    @Transactional
    public void flagStuckOrder(UUID orderId) {
        Order order = lockOrder(orderId);
        boolean stillStuck = order.getStatus() == OrderStatus.READY
                && order.getAssignmentAlertedAt() == null
                && assignmentRepository.findFirstByOrderIdAndStatusIn(orderId, DeliveryAssignmentStatus.ACTIVE).isEmpty();
        if (!stillStuck) return;

        LocalDateTime now = LocalDateTime.now();
        order.setAssignmentAlertedAt(now);
        long waiting = order.getReadyAt() == null ? 0
                : java.time.Duration.between(order.getReadyAt(), now).toMinutes();

        log.error("[OPS-ALERT] Order {} at '{}' READY for {} min without a delivery partner",
                orderId, order.getRestaurant().getName(), waiting);
        eventProducer.publishDeliveryDelayed(new OrderEvents.DeliveryDelayedEvent(
                orderId, order.getCustomer().getId(), order.getRestaurant().getId(),
                order.getRestaurant().getName(), waiting, now));
    }

    /** Admin dashboard: every READY order nobody is delivering, oldest first. */
    @Transactional(readOnly = true)
    public java.util.List<com.fooddelivery.backend.dto.response.StuckOrderResponse> listUnassignedReadyOrders() {
        LocalDateTime now = LocalDateTime.now();
        return assignmentRepository.findUnassignedReadyOrders(OrderStatus.READY, DeliveryAssignmentStatus.ACTIVE)
                .stream()
                .map(o -> com.fooddelivery.backend.dto.response.StuckOrderResponse.builder()
                        .orderId(o.getId())
                        .restaurantId(o.getRestaurant().getId())
                        .restaurantName(o.getRestaurant().getName())
                        .restaurantLatitude(o.getRestaurant().getLatitude())
                        .restaurantLongitude(o.getRestaurant().getLongitude())
                        .readyAt(o.getReadyAt())
                        .waitingMinutes(o.getReadyAt() == null ? 0
                                : java.time.Duration.between(o.getReadyAt(), now).toMinutes())
                        .alerted(o.getAssignmentAlertedAt() != null)
                        .build())
                .toList();
    }

    /**
     * Admin override: give this order to this partner.
     * Ignores distance, availability and "rejected before" — a human decided.
     * Still refuses impossible cases: wrong role, inactive, busy, order not READY.
     */
    @Transactional
    public DeliveryAssignmentResponse adminAssign(UUID orderId, UUID partnerId) {
        Order order = lockOrder(orderId); // lock order first, then partner (same order everywhere)

        if (order.getStatus() != OrderStatus.READY) {
            throw new BadRequestException("Only READY orders can be assigned (order is " + order.getStatus() + ").");
        }
        if (assignmentRepository.findFirstByOrderIdAndStatusIn(orderId, DeliveryAssignmentStatus.ACTIVE).isPresent()) {
            throw new com.fooddelivery.backend.exception.ConflictException("Order already has a delivery partner.");
        }
        User partner = userRepository.findById(partnerId)
                .filter(u -> u.getRole() == Role.DELIVERY_PARTNER && u.isActive())
                .orElseThrow(() -> new BadRequestException("Not an active delivery partner: " + partnerId));
        DeliveryPartnerStatus status = partnerStatusRepository.findByIdForUpdate(partner.getId())
                .orElseThrow(() -> new BadRequestException("This partner has never gone online in the app."));
        if (findActiveAssignmentOfPartner(partner.getId()).isPresent()) {
            throw new com.fooddelivery.backend.exception.ConflictException("Partner is busy with another delivery.");
        }

        DeliveryAssignment assignment = createAssignment(order, status);
        log.info("[DELIVERY] Admin manually assigned order {} to partner {}", orderId, partnerId);
        return toAssignmentResponse(assignment);
    }

    // ═════════════════════════════════════════════════════════
    // 3. GUARD RAILS — called by OrderService
    // ═════════════════════════════════════════════════════════

    /**
     * Returns the order's active assignment if `user` is the partner on it,
     * otherwise 403. Used before PICKED_UP / DELIVERED.
     */
    public DeliveryAssignment requireAssignedPartner(Order order, User user) {
        return assignmentRepository
                .findFirstByOrderIdAndStatusIn(order.getId(), DeliveryAssignmentStatus.ACTIVE)
                .filter(a -> a.getPartner().getId().equals(user.getId()))
                .orElseThrow(() -> new ForbiddenException(
                        "Only the delivery partner assigned to this order can do this."));
    }

    /** Order was picked up from the restaurant. */
    public void markPickedUp(Order order) {
        assignmentRepository
                .findFirstByOrderIdAndStatusIn(order.getId(), DeliveryAssignmentStatus.ACTIVE)
                .ifPresent(a -> {
                    a.setStatus(DeliveryAssignmentStatus.PICKED_UP);
                    a.setPickedUpAt(LocalDateTime.now());
                });
    }

    /**
     * Order reached the customer: close the assignment and free the partner.
     * @return the partner who delivered it (empty if there was no assignment)
     */
    public Optional<UUID> markDelivered(Order order) {
        return assignmentRepository
                .findFirstByOrderIdAndStatusIn(order.getId(), DeliveryAssignmentStatus.ACTIVE)
                .map(a -> {
                    a.setStatus(DeliveryAssignmentStatus.DELIVERED);
                    a.setDeliveredAt(LocalDateTime.now());
                    UUID partnerId = a.getPartner().getId();
                    freePartner(partnerId);
                    return partnerId;
                });
    }

    /**
     * Phase 12 geofence: you can only mark "picked up" when you're near the
     * restaurant, and "delivered" when you're near the customer.
     * Stops fake pickups/deliveries ("delivered" from home, food never came).
     *
     * Edge cases: location unknown or stale → refuse (open the app first);
     * customer address without coordinates → drop-off can't be checked → allowed.
     */
    public void checkGeofence(Order order, UUID partnerId, boolean pickup) {
        if (!geofenceEnabled) return;

        Double targetLat = pickup ? order.getRestaurant().getLatitude() : order.getDeliveryLat();
        Double targetLng = pickup ? order.getRestaurant().getLongitude() : order.getDeliveryLng();
        if (targetLat == null || targetLng == null) return; // nothing to check against

        DeliveryPartnerStatus s = partnerStatusRepository.findById(partnerId)
                .filter(p -> p.getCurrentLat() != null && p.getCurrentLng() != null)
                .orElseThrow(() -> new BadRequestException("Your location is unknown. Open the app and try again."));
        if (etaService.isStale(s)) {
            throw new BadRequestException("Your location is out of date. Open the app and try again.");
        }

        double km = com.fooddelivery.backend.util.GeoUtils.distanceKm(
                s.getCurrentLat(), s.getCurrentLng(), targetLat, targetLng);
        double allowed = pickup ? pickupRadiusKm : dropoffRadiusKm;
        if (km > allowed) {
            throw new BadRequestException(String.format("You are %.2f km from the %s (must be within %.1f km).",
                    km, pickup ? "restaurant" : "customer", allowed));
        }
    }

    /** The order's current assignment, if any (for tracking). */
    public Optional<DeliveryAssignment> activeAssignment(UUID orderId) {
        return assignmentRepository.findFirstByOrderIdAndStatusIn(orderId, DeliveryAssignmentStatus.ACTIVE);
    }

    public Optional<DeliveryPartnerStatus> partnerStatus(UUID partnerId) {
        return partnerStatusRepository.findById(partnerId);
    }

    /**
     * A partner may view an order only while delivering it or after delivering
     * it. A partner who REJECTED (or was released) loses access to the
     * customer's address.
     */
    public boolean wasAssignedTo(UUID orderId, UUID partnerId) {
        return assignmentRepository.existsByOrderIdAndPartnerIdAndStatusIn(orderId, partnerId,
                java.util.EnumSet.of(DeliveryAssignmentStatus.ASSIGNED,
                        DeliveryAssignmentStatus.PICKED_UP,
                        DeliveryAssignmentStatus.DELIVERED));
    }

    // ═════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════

    /**
     * Job over (delivered / rejected): the partner is free again — but only if
     * they still WANT orders. If they went offline mid-delivery, they stay off.
     */
    private void freePartner(UUID partnerId) {
        partnerStatusRepository.findByIdForUpdate(partnerId).ifPresent(s -> {
            s.setAvailable(s.isOnline());
            s.setLastSeenAt(LocalDateTime.now());
        });
    }

    private static void afterCommit(Runnable action) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }

    private Order lockOrder(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private Optional<DeliveryAssignment> findActiveAssignmentOfPartner(UUID partnerId) {
        return assignmentRepository.findFirstByPartnerIdAndStatusIn(partnerId, DeliveryAssignmentStatus.ACTIVE);
    }

    private PartnerStatusResponse toStatusResponse(DeliveryPartnerStatus s, DeliveryAssignment active) {
        return PartnerStatusResponse.builder()
                .partnerId(s.getPartnerId())
                .isOnline(s.isOnline())
                .isAvailable(s.isAvailable())
                .latitude(s.getCurrentLat())
                .longitude(s.getCurrentLng())
                .lastSeenAt(s.getLastSeenAt())
                .currentAssignmentId(active != null ? active.getId() : null)
                .build();
    }

    private DeliveryAssignmentResponse toAssignmentResponse(DeliveryAssignment a) {
        Order o = a.getOrder();
        Restaurant r = o.getRestaurant();
        return DeliveryAssignmentResponse.builder()
                .id(a.getId())
                .orderId(o.getId())
                .orderStatus(o.getStatus())
                .status(a.getStatus())
                .partnerId(a.getPartner().getId())
                .partnerName(a.getPartner().getName())
                .restaurantName(r.getName())
                .restaurantLatitude(r.getLatitude())
                .restaurantLongitude(r.getLongitude())
                .customerName(o.getCustomer().getName())
                .deliveryAddressText(o.getDeliveryAddressText())
                .orderTotal(o.getTotalAmount())
                .assignedAt(a.getAssignedAt())
                .pickedUpAt(a.getPickedUpAt())
                .deliveredAt(a.getDeliveredAt())
                .build();
    }
}
