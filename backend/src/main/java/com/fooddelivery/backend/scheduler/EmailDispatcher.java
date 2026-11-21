package com.fooddelivery.backend.scheduler;

import com.fooddelivery.backend.entity.EmailStatus;
import com.fooddelivery.backend.entity.Notification;
import com.fooddelivery.backend.entity.User;
import com.fooddelivery.backend.notification.EmailSender;
import com.fooddelivery.backend.repository.NotificationRepository;
import com.fooddelivery.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * EmailDispatcher — sends notification emails from the outbox, with retries.
 *
 * CONCEPT: Transactional Outbox
 * ─────────────────────────────
 * Before: the Kafka consumer sent email directly. A slow or down mail
 * server would block the consumer, and a failed email was simply lost.
 * Now: the consumer only writes a row (email_status = PENDING) in the SAME
 * transaction as the in-app notification. This job, every few seconds,
 * picks due rows and sends them:
 *   success → SENT
 *   failure → retry later with exponential backoff: 30s, 1m, 2m, 4m ...
 *   too many failures → FAILED (kept for inspection, never retried)
 * Nothing is lost if the mail server is down for an hour.
 *
 * Multi-instance safe without ShedLock: lockDueEmails() uses
 * FOR UPDATE SKIP LOCKED, so instances share the work instead of
 * duplicating it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailDispatcher {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;

    @Value("${app.email.batch-size:20}")
    private int batchSize;

    @Value("${app.email.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.email.base-backoff:PT30S}")
    private Duration baseBackoff;

    @Scheduled(fixedDelayString = "${app.email.dispatch-interval-ms:5000}")
    @Transactional
    public void dispatchDueEmails() {
        List<Notification> due = notificationRepository.lockDueEmails(batchSize);
        for (Notification n : due) {
            String to = userRepository.findById(n.getUserId()).map(User::getEmail).orElse(null);
            if (to == null) {
                n.setEmailStatus(EmailStatus.SKIPPED); // user gone — nobody to email
                continue;
            }
            try {
                emailSender.send(to, n.getTitle(), n.getMessage() + "\n\n— Food Delivery");
                n.setEmailStatus(EmailStatus.SENT);
                n.setEmailSentAt(LocalDateTime.now());
                n.setEmailLastError(null);
            } catch (Exception e) {
                scheduleRetry(n, e);
            }
        }
    }

    /** Exponential backoff: base × 2^(attempt-1), capped at 1 hour. */
    private void scheduleRetry(Notification n, Exception e) {
        int attempts = n.getEmailAttempts() + 1;
        n.setEmailAttempts(attempts);
        n.setEmailLastError(truncate(e.getMessage()));

        if (attempts >= maxAttempts) {
            n.setEmailStatus(EmailStatus.FAILED);
            log.error("[EMAIL] Giving up on notification {} after {} attempts: {}", n.getId(), attempts, e.getMessage());
            return;
        }
        long backoffSeconds = Math.min(baseBackoff.getSeconds() << (attempts - 1), 3600);
        n.setEmailNextAttemptAt(LocalDateTime.now().plusSeconds(backoffSeconds));
        log.warn("[EMAIL] Send failed for notification {} (attempt {}), retry in {}s: {}",
                n.getId(), attempts, backoffSeconds, e.getMessage());
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
