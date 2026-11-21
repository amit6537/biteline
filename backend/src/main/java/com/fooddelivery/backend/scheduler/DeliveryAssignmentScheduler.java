package com.fooddelivery.backend.scheduler;

import com.fooddelivery.backend.entity.DeliveryAssignmentStatus;
import com.fooddelivery.backend.entity.OrderStatus;
import com.fooddelivery.backend.repository.DeliveryAssignmentRepository;
import com.fooddelivery.backend.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * DeliveryAssignmentScheduler — the "safety net" for stuck orders.
 *
 * WHY do we need this if Kafka already triggers assignment?
 *   1. No partner was free when the order became READY → the Kafka message
 *      is consumed and done. Someone must try again later.
 *   2. A partner rejected and nobody else was free.
 *   3. The app crashed right after commit, before sending order.ready.
 *   4. The partner's phone died AFTER being assigned, before pickup
 *      (step 1 below releases those jobs).
 * Every few seconds this job asks the DB: "which READY orders have nobody
 * delivering them?" and retries each one.
 *
 * CONCEPT: @Scheduled(fixedDelay)
 *   fixedDelay = wait N ms AFTER the previous run finished, then run again.
 *   (fixedRate would start every N ms even if the last run is still going.)
 *
 * Several app instances? @SchedulerLock (ShedLock) makes sure only ONE of
 * them runs each round. (Even without it, it would be safe thanks to row
 * locks + unique index — just duplicated work.)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryAssignmentScheduler {

    private static final int BATCH_SIZE = 50;

    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryService deliveryService;

    @org.springframework.beans.factory.annotation.Value("${app.delivery.partner-stale-minutes:10}")
    private long partnerStaleMinutes;

    /** READY this long with no partner → alert ops (once per order). */
    @org.springframework.beans.factory.annotation.Value("${app.delivery.stuck-alert-minutes:10}")
    private long stuckAlertMinutes;

    @Scheduled(
            fixedDelayString = "${app.delivery.retry-interval-ms:30000}",
            initialDelayString = "${app.delivery.retry-interval-ms:30000}")
    @SchedulerLock(name = "deliveryAssignmentRetry", lockAtMostFor = "PT2M", lockAtLeastFor = "PT1S")
    public void retryUnassignedOrders() {
        // Step 1: free up orders stuck with a partner whose phone went silent
        List<UUID> stale = assignmentRepository.findStaleAssignmentIds(
                DeliveryAssignmentStatus.ASSIGNED,
                java.time.LocalDateTime.now().minusMinutes(partnerStaleMinutes));
        for (UUID assignmentId : stale) {
            try {
                deliveryService.releaseStaleAssignment(assignmentId);
            } catch (Exception e) {
                log.error("[SCHEDULER] Could not release stale assignment {}: {}", assignmentId, e.getMessage());
            }
        }

        // Step 2: (re)try every READY order that has nobody delivering it
        List<UUID> orderIds = assignmentRepository.findUnassignedOrderIds(
                OrderStatus.READY, DeliveryAssignmentStatus.ACTIVE, PageRequest.of(0, BATCH_SIZE));

        if (orderIds.isEmpty()) {
            return;
        }
        retry(orderIds);

        // Step 3: still nobody after a long wait? A human must look at it.
        List<UUID> stuck = assignmentRepository.findStuckOrderIdsNotAlerted(
                OrderStatus.READY, DeliveryAssignmentStatus.ACTIVE,
                java.time.LocalDateTime.now().minusMinutes(stuckAlertMinutes));
        for (UUID orderId : stuck) {
            try {
                deliveryService.flagStuckOrder(orderId);
            } catch (Exception e) {
                log.error("[SCHEDULER] Could not raise stuck alert for order {}: {}", orderId, e.getMessage());
            }
        }
    }

    private void retry(List<UUID> orderIds) {
        log.info("[SCHEDULER] Retrying assignment for {} unassigned READY order(s)", orderIds.size());

        for (UUID orderId : orderIds) {
            try {
                // Each call is its own transaction: one bad order can't block the rest
                deliveryService.assignPartner(orderId);
            } catch (Exception e) {
                log.error("[SCHEDULER] Assignment failed for order {}: {}", orderId, e.getMessage());
            }
        }
    }
}
