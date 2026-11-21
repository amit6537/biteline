package com.fooddelivery.backend.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.backend.service.DeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * DeliveryEventConsumer — listens for "order is READY" and triggers assignment.
 *
 * CONCEPT: Async consumer
 * ───────────────────────
 * The owner's "mark READY" request returns immediately. Finding a partner
 * happens here, in the background, a few milliseconds later. If assignment
 * is slow or fails, the owner's request is never affected.
 *
 * This class is deliberately thin: it only turns a Kafka message into a
 * method call. All the rules live in DeliveryService, so the scheduler can
 * reuse exactly the same logic.
 *
 * If assignPartner throws (e.g. DB down), the exception escapes →
 * KafkaConfig's error handler retries 3× and then parks the message in
 * order.ready.DLT.
 */
@Component
@Slf4j
public class DeliveryEventConsumer {

    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    public DeliveryEventConsumer(DeliveryService deliveryService, ObjectMapper objectMapper) {
        this.deliveryService = deliveryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "#{T(com.fooddelivery.backend.config.KafkaConfig).TOPIC_ORDER_READY}",
            groupId = "delivery-assignment-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderReady(String message) throws JsonProcessingException {
        OrderEvents.OrderReadyEvent event = objectMapper.readValue(message, OrderEvents.OrderReadyEvent.class);
        log.info("[DELIVERY] 🛵 Order {} is ready. Looking for the nearest partner...", event.orderId());

        // Empty result = nobody free right now. Not an error:
        // DeliveryAssignmentScheduler will retry this order shortly.
        deliveryService.assignPartner(event.orderId());
    }
}
