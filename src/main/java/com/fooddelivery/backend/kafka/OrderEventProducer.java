package com.fooddelivery.backend.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

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

    private void send(String topic, String key, Object event) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
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
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event for topic='{}' key='{}': {}", topic, key, e.getMessage());
        }
    }
}
