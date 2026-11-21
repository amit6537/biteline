package com.fooddelivery.backend.service;

import com.fooddelivery.backend.config.CacheConfig;
import com.fooddelivery.backend.dto.response.NotificationResponse;
import com.fooddelivery.backend.entity.Notification;
import com.fooddelivery.backend.entity.NotificationType;
import com.fooddelivery.backend.entity.OrderStatus;
import com.fooddelivery.backend.entity.PaymentStatus;
import com.fooddelivery.backend.entity.Role;
import com.fooddelivery.backend.entity.User;
import com.fooddelivery.backend.exception.ResourceNotFoundException;
import com.fooddelivery.backend.kafka.OrderEvents;
import com.fooddelivery.backend.repository.NotificationRepository;
import com.fooddelivery.backend.repository.PaymentRepository;
import com.fooddelivery.backend.repository.RestaurantRepository;
import com.fooddelivery.backend.util.PageUtils;
import com.fooddelivery.backend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * NotificationService — Phase 10.
 *
 * Two halves:
 *   A. WRITE side — called by NotificationEventConsumer for each Kafka event.
 *      Decides WHO should be told WHAT, saves it to the inbox (idempotently).
 *      The same row is the email "outbox": EmailDispatcher sends it later.
 *   B. READ side — the REST API behind the bell icon: list, unread count,
 *      mark read.
 *
 * Edge cases handled:
 *   - Same event delivered twice by Kafka   → insertIfAbsent ignores the copy
 *   - Recipient user deleted                → insert skipped, no crash/retry loop
 *   - Restaurant gone when we look up owner → owner notification skipped
 *   - Mail server down                      → EmailDispatcher retries with backoff
 *   - Transaction rolls back                → no inbox row, so no email either
 *   - Consumers lagging behind              → created_at = time the EVENT happened
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RestaurantRepository restaurantRepository;
    private final PaymentRepository paymentRepository;
    private final com.fooddelivery.backend.repository.UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final CacheManager cacheManager;
    private final com.fooddelivery.backend.config.CacheHealer cacheHealer;

    // ═════════════════════════════════════════════════════════
    // A. WRITE SIDE — one method per Kafka event
    // ═════════════════════════════════════════════════════════

    @Transactional
    public void onOrderPlaced(OrderEvents.OrderPlacedEvent e) {
        String key = "order.placed:" + e.orderId();
        notify(e.customerId(), NotificationType.ORDER_PLACED, "Order placed",
                "We've sent your order to " + e.restaurantName() + ". Total ₹" + e.totalAmount() + ".",
                e.orderId(), key, e.placedAt());

        restaurantRepository.findOwnerIdById(e.restaurantId()).ifPresent(ownerId ->
                notify(ownerId, NotificationType.NEW_ORDER, "New order received",
                        "Order #" + shortId(e.orderId()) + " from " + e.customerName()
                                + " (₹" + e.totalAmount() + "). Please accept it.",
                        e.orderId(), key, e.placedAt()));
    }

    @Transactional
    public void onPaymentSuccess(OrderEvents.PaymentSuccessEvent e) {
        notify(e.customerId(), NotificationType.PAYMENT_SUCCESS, "Payment received",
                "₹" + e.amount() + " paid for order #" + shortId(e.orderId())
                        + ". Transaction " + e.transactionId() + ".",
                e.orderId(), "payment.success:" + e.paymentId(), e.paidAt());
    }

    @Transactional
    public void onOrderStatusChanged(OrderEvents.OrderStatusChangedEvent e) {
        // The state machine never repeats a status for one order,
        // so orderId + newStatus uniquely identifies this event.
        String key = "order.status:" + e.orderId() + ":" + e.newStatus();

        if (e.newStatus() == OrderStatus.CANCELLED) {
            // Mention the refund only if one actually happened
            boolean refunded = paymentRepository.findByOrderId(e.orderId())
                    .map(p -> p.getStatus() == PaymentStatus.REFUNDED)
                    .orElse(false);
            notify(e.customerId(), NotificationType.ORDER_CANCELLED, "Order cancelled",
                    "Order #" + shortId(e.orderId()) + " was cancelled."
                            + (refunded ? " Your refund has been initiated." : ""),
                    e.orderId(), key, e.changedAt());

            restaurantRepository.findOwnerIdById(e.restaurantId()).ifPresent(ownerId ->
                    notify(ownerId, NotificationType.ORDER_CANCELLED, "Order cancelled",
                            "Order #" + shortId(e.orderId()) + " was cancelled. Please stop preparing it.",
                            e.orderId(), key, e.changedAt()));
            return;
        }

        String[] text = switch (e.newStatus()) {
            case ACCEPTED  -> new String[]{"Order accepted", e.restaurantName() + " accepted your order."};
            case PREPARING -> new String[]{"Being prepared", e.restaurantName() + " is preparing your food."};
            case READY     -> new String[]{"Ready for pickup", "Your food is ready. Finding a delivery partner..."};
            case PICKED_UP -> new String[]{"On the way", "Your order has been picked up and is on its way!"};
            case DELIVERED -> new String[]{"Delivered", "Your order has been delivered. Enjoy your meal!"};
            default        -> null; // PLACED never arrives as a "change"
        };
        if (text != null) {
            notify(e.customerId(), NotificationType.ORDER_STATUS, text[0], text[1],
                    e.orderId(), key, e.changedAt());
        }
    }

    @Transactional
    public void onDeliveryAssigned(OrderEvents.DeliveryAssignedEvent e) {
        // Keyed by ASSIGNMENT id: if the first partner rejects, the customer
        // is (correctly) told about the second partner too.
        String key = "delivery.assigned:" + e.assignmentId();
        notify(e.customerId(), NotificationType.PARTNER_ASSIGNED, "Delivery partner assigned",
                e.partnerName() + " will pick up your order.",
                e.orderId(), key, e.assignedAt());
        notify(e.partnerId(), NotificationType.NEW_DELIVERY_JOB, "New delivery job",
                "Pick up order #" + shortId(e.orderId()) + ". Open the app for the address.",
                e.orderId(), key, e.assignedAt());
    }

    @Transactional
    public void onOrderDelivered(OrderEvents.OrderDeliveredEvent e) {
        notify(e.customerId(), NotificationType.REVIEW_REQUEST, "How was your food?",
                "Rate your order #" + shortId(e.orderId()) + " — it helps other customers.",
                e.orderId(), "order.delivered:" + e.orderId(), e.deliveredAt());
    }

    /** Order stuck without a partner: reassure the customer, wake up the admins. */
    @Transactional
    public void onDeliveryDelayed(OrderEvents.DeliveryDelayedEvent e) {
        String key = "delivery.delayed:" + e.orderId();
        notify(e.customerId(), NotificationType.DELIVERY_DELAYED, "Slight delay",
                "Your food is ready and we're still finding a delivery partner. Thanks for your patience!",
                e.orderId(), key, e.detectedAt());

        for (User admin : userRepository.findByRoleAndIsActiveTrue(Role.ADMIN)) {
            notify(admin.getId(), NotificationType.OPS_ALERT, "🚨 Order stuck without a partner",
                    "Order #" + shortId(e.orderId()) + " at " + e.restaurantName() + " has been READY for "
                            + e.waitingMinutes() + " min with no delivery partner. "
                            + "Assign one manually: POST /api/admin/orders/" + e.orderId() + "/assign",
                    e.orderId(), key, e.detectedAt());
        }
    }

    /** New review → tell the restaurant owner (and the partner, if rated). */
    @Transactional
    public void onReviewCreated(OrderEvents.ReviewCreatedEvent e) {
        String key = "review.created:" + e.reviewId();
        restaurantRepository.findOwnerIdById(e.restaurantId()).ifPresent(ownerId ->
                notify(ownerId, NotificationType.NEW_REVIEW, "New review: " + "★".repeat(e.foodRating()),
                        "A customer rated order #" + shortId(e.orderId()) + " " + e.foodRating() + "/5.",
                        e.orderId(), key, e.createdAt()));
        if (e.partnerId() != null && e.deliveryRating() != null) {
            notify(e.partnerId(), NotificationType.NEW_REVIEW, "Delivery rated " + e.deliveryRating() + "/5",
                    "A customer rated your delivery of order #" + shortId(e.orderId()) + ".",
                    e.orderId(), key, e.createdAt());
        }
    }

    /**
     * Save to inbox (idempotent). The row is also the email outbox entry.
     */
    private void notify(UUID userId, NotificationType type, String title, String message,
                        UUID orderId, String eventKey, LocalDateTime occurredAt) {
        int inserted = notificationRepository.insertIfAbsent(userId, type.name(), title, message,
                orderId, eventKey, occurredAt != null ? occurredAt : LocalDateTime.now());

        if (inserted == 0) {
            log.debug("Notification skipped (duplicate event or user gone): user={} key={}", userId, eventKey);
            return; // already sent before → don't email twice either
        }

        evictUnreadCount(userId); // badge number changed
        // Email: nothing to do here — the row was inserted with
        // email_status = PENDING and EmailDispatcher will send it (with retries).
    }

    // ═════════════════════════════════════════════════════════
    // B. READ SIDE — REST API
    // ═════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(boolean unreadOnly, int page, int size) {
        UUID userId = securityUtils.getCurrentUserId();
        var pageable = PageUtils.of(page, size);
        Page<Notification> result = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return result.map(this::toResponse);
    }

    /**
     * The bell badge number. Apps poll this every few seconds, so it's cached
     * (Phase 9 + 10 together). Evicted whenever it can change.
     */
    @Cacheable(cacheNames = CacheConfig.UNREAD_COUNT, key = "#userId")
    @Transactional(readOnly = true)
    public Long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    /** Idempotent: marking an already-read notification again is fine. */
    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        UUID userId = securityUtils.getCurrentUserId();
        Notification n = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(LocalDateTime.now());
            evictUnreadCount(userId);
        }
        return toResponse(n);
    }

    @Transactional
    public int markAllRead() {
        UUID userId = securityUtils.getCurrentUserId();
        int updated = notificationRepository.markAllRead(userId, LocalDateTime.now());
        evictUnreadCount(userId);
        return updated;
    }

    // ═════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════

    /**
     * Manual evict (not @CacheEvict): notify() is called from inside this
     * class, and Spring's annotations don't work on self-calls (the call
     * doesn't pass through the proxy).
     * Runs AFTER commit (so nobody re-caches the old count in between) and
     * never throws: if Redis is down, CacheHealer cleans up later.
     */
    private void evictUnreadCount(UUID userId) {
        afterCommit(() -> {
            try {
                Cache cache = cacheManager.getCache(CacheConfig.UNREAD_COUNT);
                if (cache != null) cache.evict(userId);
            } catch (RuntimeException e) {
                log.warn("Could not evict unread count for {}: {}", userId, e.getMessage());
                cacheHealer.markPossiblyStale();
            }
        });
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** First 8 characters of a UUID — easier to read in a message. */
    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .orderId(n.getOrderId())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .build();
    }
}
