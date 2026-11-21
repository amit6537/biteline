package com.fooddelivery.backend.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.backend.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * NotificationEventConsumer — Phase 10: turns Kafka events into notifications.
 *
 * CONCEPT: Async Kafka consumers
 *   OrderService / PaymentService / DeliveryService never call notification
 *   code. They just publish events. This class listens and reacts. If
 *   notifications are slow or broken, ordering and paying still work.
 *
 * Thin on purpose: parse JSON → call NotificationService. Each listener has
 * its own topic; all share the "notification-group" consumer group.
 *
 * Failure handling (from KafkaConfig): an exception here → 3 retries →
 * message parked in "<topic>.DLT". Malformed JSON skips the retries.
 *
 * Ordering: events are keyed by orderId, so all events of ONE order on ONE
 * topic land on the same partition and are processed in order.
 */
@Component
@Slf4j
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public NotificationEventConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = "#{T(com.fooddelivery.backend.config.KafkaConfig).TOPIC_ORDER_PLACED}",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderPlaced(String message) throws JsonProcessingException {
        notificationService.onOrderPlaced(read(message, OrderEvents.OrderPlacedEvent.class));
    }

    @KafkaListener(
            topics = "#{T(com.fooddelivery.backend.config.KafkaConfig).TOPIC_PAYMENT_SUCCESS}",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentSuccess(String message) throws JsonProcessingException {
        notificationService.onPaymentSuccess(read(message, OrderEvents.PaymentSuccessEvent.class));
    }

    @KafkaListener(
            topics = "#{T(com.fooddelivery.backend.config.KafkaConfig).TOPIC_ORDER_STATUS_CHANGED}",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderStatusChanged(String message) throws JsonProcessingException {
        notificationService.onOrderStatusChanged(read(message, OrderEvents.OrderStatusChangedEvent.class));
    }

    @KafkaListener(
            topics = "#{T(com.fooddelivery.backend.config.KafkaConfig).TOPIC_ORDER_DELIVERED}",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderDelivered(String message) throws JsonProcessingException {
        notificationService.onOrderDelivered(read(message, OrderEvents.OrderDeliveredEvent.class));
    }

    @KafkaListener(
            topics = "#{T(com.fooddelivery.backend.config.KafkaConfig).TOPIC_DELIVERY_ASSIGNED}",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDeliveryAssigned(String message) throws JsonProcessingException {
        notificationService.onDeliveryAssigned(read(message, OrderEvents.DeliveryAssignedEvent.class));
    }

    @KafkaListener(
            topics = "#{T(com.fooddelivery.backend.config.KafkaConfig).TOPIC_DELIVERY_DELAYED}",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDeliveryDelayed(String message) throws JsonProcessingException {
        notificationService.onDeliveryDelayed(read(message, OrderEvents.DeliveryDelayedEvent.class));
    }

    @KafkaListener(
            topics = "#{T(com.fooddelivery.backend.config.KafkaConfig).TOPIC_REVIEW_CREATED}",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onReviewCreated(String message) throws JsonProcessingException {
        notificationService.onReviewCreated(read(message, OrderEvents.ReviewCreatedEvent.class));
    }

    private <T> T read(String message, Class<T> type) throws JsonProcessingException {
        T event = objectMapper.readValue(message, type);
        log.debug("[NOTIFICATION] received {}", event);
        return event;
    }
}
