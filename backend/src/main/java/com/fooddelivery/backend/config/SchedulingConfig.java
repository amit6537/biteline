package com.fooddelivery.backend.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Scheduling + ShedLock.
 *
 * @EnableScheduling  → turns on @Scheduled methods.
 * @EnableSchedulerLock → turns on @SchedulerLock: before a locked job runs,
 *   ShedLock tries to take a row in the "shedlock" table. Only the instance
 *   that gets it runs the job; the others skip that round.
 *
 *   lockAtMostFor  = safety: if the instance dies mid-job, the lock expires
 *                    after this long so another instance can take over.
 *   lockAtLeastFor = keep the lock a bit even if the job was quick, so a
 *                    second instance with a slightly different clock doesn't
 *                    run the same round right after.
 *
 * usingDbTime() → all instances use Postgres' clock, not their own
 * (servers' clocks are never perfectly in sync).
 *
 * Which jobs are locked?
 *   DeliveryAssignmentScheduler, NotificationCleanupScheduler → yes.
 *   EmailDispatcher → no: it uses SKIP LOCKED, so instances share the work.
 *   CacheHealer     → no: each instance must heal its OWN stale flag.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
