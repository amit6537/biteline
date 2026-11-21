package com.fooddelivery.backend.scheduler;

import com.fooddelivery.backend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Every login and every refresh adds a row to refresh_tokens.
 * Once a token is past its expiry its JWT is dead anyway, so the row is
 * useless → delete it every night (3:30 AM). Revoked-but-not-expired rows
 * are kept: they are what lets us spot a stolen token being reused.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "${app.auth.refresh-token-cleanup-cron:0 30 3 * * *}")
    @SchedulerLock(name = "refreshTokenCleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public void deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("[SCHEDULER] Deleted {} expired refresh tokens", deleted);
        }
    }
}
