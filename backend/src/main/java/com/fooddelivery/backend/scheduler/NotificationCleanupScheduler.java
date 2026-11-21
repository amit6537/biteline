package com.fooddelivery.backend.scheduler;

import com.fooddelivery.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * NotificationCleanupScheduler — keeps the notifications table from growing forever.
 *
 * Every night at 3 AM (cron "0 0 3 * * *" = sec min hour day month weekday):
 *   - delete READ notifications older than 30 days
 *   - delete ANY notification older than 90 days
 * Unread recent ones are never touched.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationCleanupScheduler {

    private final NotificationRepository notificationRepository;

    @Value("${app.notifications.keep-read-days:30}")
    private int keepReadDays;

    @Value("${app.notifications.keep-any-days:90}")
    private int keepAnyDays;

    @Scheduled(cron = "${app.notifications.cleanup-cron:0 0 3 * * *}")
    @SchedulerLock(name = "notificationCleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public void deleteOldNotifications() {
        LocalDateTime now = LocalDateTime.now();
        int deleted = notificationRepository.deleteOld(now.minusDays(keepReadDays), now.minusDays(keepAnyDays));
        if (deleted > 0) {
            log.info("[SCHEDULER] Deleted {} old notifications", deleted);
        }
    }
}
