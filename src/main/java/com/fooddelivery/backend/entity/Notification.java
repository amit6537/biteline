package com.fooddelivery.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Notification — one message in a user's in-app inbox.
 *
 * userId is a plain UUID column (not a @ManyToOne User): we never need the
 * User object here, so we skip loading it.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "event_key", nullable = false, length = 200)
    private String eventKey;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    // ── Email outbox fields (sent by EmailDispatcher, with retries) ──
    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false, length = 20)
    @Builder.Default
    private EmailStatus emailStatus = EmailStatus.PENDING;

    @Column(name = "email_attempts", nullable = false)
    private int emailAttempts;

    @Column(name = "email_next_attempt_at")
    private LocalDateTime emailNextAttemptAt;

    @Column(name = "email_last_error", columnDefinition = "TEXT")
    private String emailLastError;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;
}
