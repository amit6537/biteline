package com.fooddelivery.backend.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

/**
 * OrderEventProducer — Serializes event records to JSON and publishes to Kafka.
 */
@Component
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // Explicit constructor
    public OrderEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishOrderPlaced(OrderEvents.OrderPlacedEvent event) {
        send(com.fooddelivery.backend.config.KafkaConfig.TOPIC_ORDER_PLACED,
                event.orderId().toString(), event);
    }

    public void publishPaymentSuccess(OrderEvents.PaymentSuccessEvent event) {
        send(com.fooddelivery.backend.config.KafkaConfig.TOPIC_PAYMENT_SUCCESS,
                event.orderId().toString(), event);
    }

    public void publishOrderStatusChanged(OrderEvents.OrderStatusChangedEvent event) {
        send(com.fooddelivery.backend.config.KafkaConfig.TOPIC_ORDER_STATUS_CHANGED,
                event.orderId().toString(), event);
    }

    public void publishOrderReady(OrderEvents.OrderReadyEvent event) {
        send(com.fooddelivery.backend.config.KafkaConfig.TOPIC_ORDER_READY,
                event.orderId().toString(), event);
    }

    public void publishOrderDelivered(OrderEvents.OrderDeliveredEvent event) {
        send(com.fooddelivery.backend.config.KafkaConfig.TOPIC_ORDER_DELIVERED,
                event.orderId().toString(), event);
    }

    /**
     * CONCEPT: Publish AFTER commit
     * ──────────────────────────────
     * Services call publishXxx() in the middle of a @Transactional method.
     * If we sent to Kafka immediately:
     *   - the transaction could still roll back → consumers act on an order
     *     that doesn't exist ("ghost event");
     *   - a consumer could read the DB before our commit lands and see stale
     *     data (e.g. DeliveryEventConsumer seeing PREPARING instead of READY).
     * So inside a transaction we only *register* the send, and Spring runs it
     * right after the commit succeeds. On rollback, nothing is sent.
     *
     * (Still not 100%: if the app crashes between commit and send the event
     * is lost. The full fix is the "Transactional Outbox" pattern.)
     */
    public void publishDeliveryAssigned(OrderEvents.DeliveryAssignedEvent event) {
        send(com.fooddelivery.backend.config.KafkaConfig.TOPIC_DELIVERY_ASSIGNED,
                event.orderId().toString(), event);
    }

    public void publishDeliveryDelayed(OrderEvents.DeliveryDelayedEvent event) {
        send(com.fooddelivery.backend.config.KafkaConfig.TOPIC_DELIVERY_DELAYED,
                event.orderId().toString(), event);
    }

    public void publishReviewCreated(OrderEvents.ReviewCreatedEvent event) {
        send(com.fooddelivery.backend.config.KafkaConfig.TOPIC_REVIEW_CREATED,
                event.orderId().toString(), event);
    }

    private void send(String topic, String key, Object event) {
        final String jsonPayload;
        try {
            // Serialize now, so a bad event fails loudly inside the request
            jsonPayload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event for topic " + topic, e);
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(topic, key, jsonPayload);
                }
            });
        } else {
            doSend(topic, key, jsonPayload);
        }
    }

    private void doSend(String topic, String key, String jsonPayload) {
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, jsonPayload);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Published to topic='{}' key='{}' offset={}",
                        topic, key, result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish to topic='{}' key='{}': {}",
                        topic, key, ex.getMessage());
            }
        });
    }
}
