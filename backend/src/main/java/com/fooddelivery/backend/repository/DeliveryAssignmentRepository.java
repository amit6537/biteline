package com.fooddelivery.backend.repository;

import com.fooddelivery.backend.entity.DeliveryAssignment;
import com.fooddelivery.backend.entity.DeliveryAssignmentStatus;
import com.fooddelivery.backend.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {

    // The current (ASSIGNED / PICKED_UP) assignment of an order, if any
    Optional<DeliveryAssignment> findFirstByOrderIdAndStatusIn(
            UUID orderId, Collection<DeliveryAssignmentStatus> statuses);

    // The partner's current job, if any
    Optional<DeliveryAssignment> findFirstByPartnerIdAndStatusIn(
            UUID partnerId, Collection<DeliveryAssignmentStatus> statuses);

    // Is/was this partner assigned this order in one of these states?
    // (used for "can view order": a partner who REJECTED loses access)
    boolean existsByOrderIdAndPartnerIdAndStatusIn(
            UUID orderId, UUID partnerId, Collection<DeliveryAssignmentStatus> statuses);

    /** Just the order id of an assignment — no entity loaded (so nothing stale gets cached). */
    @Query("SELECT a.order.id FROM DeliveryAssignment a WHERE a.id = :id")
    Optional<UUID> findOrderIdById(@Param("id") UUID id);

    /** Just the order id of an assignment owned by this partner — no entity loaded. */
    @Query("SELECT a.order.id FROM DeliveryAssignment a WHERE a.id = :id AND a.partner.id = :partnerId")
    Optional<UUID> findOrderIdByIdAndPartnerId(@Param("id") UUID id, @Param("partnerId") UUID partnerId);

    /**
     * ASSIGNED (not yet picked up) jobs whose partner has gone silent —
     * phone died, app killed, no network. These orders must be given to
     * someone else, or the food sits at the restaurant forever.
     */
    @Query("""
            SELECT a.id FROM DeliveryAssignment a, DeliveryPartnerStatus s
            WHERE s.partnerId = a.partner.id
              AND a.status = :assigned
              AND s.lastSeenAt < :seenBefore
            """)
    List<UUID> findStaleAssignmentIds(
            @Param("assigned") DeliveryAssignmentStatus assigned,
            @Param("seenBefore") java.time.LocalDateTime seenBefore);

    // Partner's delivery history, newest first
    Page<DeliveryAssignment> findByPartnerIdOrderByAssignedAtDesc(UUID partnerId, Pageable pageable);

    /**
     * READY orders that have been waiting for a partner longer than allowed
     * and haven't triggered an ops alert yet.
     */
    @Query("""
            SELECT o.id FROM Order o
            WHERE o.status = :ready
              AND o.readyAt < :readyBefore
              AND o.assignmentAlertedAt IS NULL
              AND NOT EXISTS (SELECT 1 FROM DeliveryAssignment a
                              WHERE a.order = o AND a.status IN :active)
            """)
    List<UUID> findStuckOrderIdsNotAlerted(
            @Param("ready") OrderStatus ready,
            @Param("active") Collection<DeliveryAssignmentStatus> active,
            @Param("readyBefore") java.time.LocalDateTime readyBefore);

    /** All READY orders without a partner (admin dashboard). */
    @Query("""
            SELECT o FROM Order o
            WHERE o.status = :ready
              AND NOT EXISTS (SELECT 1 FROM DeliveryAssignment a
                              WHERE a.order = o AND a.status IN :active)
            ORDER BY o.readyAt
            """)
    List<com.fooddelivery.backend.entity.Order> findUnassignedReadyOrders(
            @Param("ready") OrderStatus ready,
            @Param("active") Collection<DeliveryAssignmentStatus> active);

    /**
     * READY orders that nobody is delivering yet — the scheduler retries these.
     * (They got stuck because no partner was free when they became READY.)
     */
    @Query("""
            SELECT o.id FROM Order o
            WHERE o.status = :ready
              AND NOT EXISTS (SELECT 1 FROM DeliveryAssignment a
                              WHERE a.order = o AND a.status IN :active)
            ORDER BY o.readyAt
            """)
    List<UUID> findUnassignedOrderIds(
            @Param("ready") OrderStatus ready,
            @Param("active") Collection<DeliveryAssignmentStatus> active,
            Pageable pageable);
}
