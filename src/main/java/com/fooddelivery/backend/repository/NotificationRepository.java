package com.fooddelivery.backend.repository;

import com.fooddelivery.backend.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Insert a notification ONLY if it doesn't exist yet. Returns 1 if inserted,
     * 0 if it was a duplicate (or the user no longer exists).
     *
     * CONCEPT: Idempotent insert
     *   ON CONFLICT (user_id, event_key) DO NOTHING → a replayed Kafka event
     *   is silently ignored instead of creating a second notification.
     *   WHERE EXISTS (user) → if the user was deleted, insert nothing instead
     *   of crashing on the foreign key (which would retry forever → DLT).
     *
     * One SQL statement = no race between "check" and "insert".
     * New rows start with email_status = PENDING → EmailDispatcher sends them.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO notifications (id, user_id, type, title, message, order_id, event_key, is_read, created_at,
                                       email_status, email_next_attempt_at)
            SELECT gen_random_uuid(), :userId, :type, :title, :message, :orderId, :eventKey, FALSE, :createdAt,
                   'PENDING', NOW()
            WHERE EXISTS (SELECT 1 FROM users WHERE id = :userId)
            ON CONFLICT (user_id, event_key) DO NOTHING
            """)
    int insertIfAbsent(@Param("userId") UUID userId,
                       @Param("type") String type,
                       @Param("title") String title,
                       @Param("message") String message,
                       @Param("orderId") UUID orderId,
                       @Param("eventKey") String eventKey,
                       @Param("createdAt") LocalDateTime createdAt);

    /**
     * Email outbox: grab up to :limit due emails AND lock them.
     * SKIP LOCKED → if two app instances run the dispatcher at once, each
     * gets DIFFERENT rows. No email is sent twice, no ShedLock needed.
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM notifications
            WHERE email_status = 'PENDING' AND email_next_attempt_at <= NOW()
            ORDER BY email_next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """)
    java.util.List<Notification> lockDueEmails(@Param("limit") int limit);

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    // Ownership check built into the query: someone else's id → empty → 404
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    /** One UPDATE for "mark all as read", instead of loading every row. */
    @Modifying
    @Query("""
            UPDATE Notification n SET n.read = true, n.readAt = :now
            WHERE n.userId = :userId AND n.read = false
            """)
    int markAllRead(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /** Retention: drop old read notifications, and anything very old. */
    @Modifying
    @Query("""
            DELETE FROM Notification n
            WHERE (n.read = true AND n.createdAt < :readBefore)
               OR n.createdAt < :anyBefore
            """)
    int deleteOld(@Param("readBefore") LocalDateTime readBefore, @Param("anyBefore") LocalDateTime anyBefore);
}
