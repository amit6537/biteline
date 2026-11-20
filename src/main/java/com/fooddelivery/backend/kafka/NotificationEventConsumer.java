package com.fooddelivery.backend.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * NotificationEventConsumer — listens for order lifecycle events and sends notifications.
 */
@Component
@Slf4j
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;

    // Explicit constructor: completely satisfies IDE language server without Lombok processing delays
    public NotificationEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "#{T(com.fooddelivery.backend.config.KafkaConfig).TOPIC_ORDER_PLACED}",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderPlaced(String message) {
        try {
            OrderEvents.OrderPlacedEvent event = objectMapper.readValue(message, OrderEvents.OrderPlacedEvent.class);
            log.info("[NOTIFICATION] 📦 Order placed! Sending confirmation to {}. OrderId={}, Total=₹{}",
                    event.customerEmail(), event.orderId(), event.totalAmount());
        } catch (Exception e) {
            log.error("[NOTIFICATION] Error deserializing order.placed event: {}", e.getMessage());
        }
    }

    @KafkaListener(
            topics = "#{T(com.fooddelivery.backend.config.KafkaConfig).TOPIC_PAYMENT_SUCCESS}",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentSuccess(String message) {
        try {
            OrderEvents.PaymentSuccessEvent event = objectMapper.readValue(message, OrderEvents.PaymentSuccessEvent.class);
            log.info("[NOTIFICATION] 💳 Payment confirmed! Email={}, Amount=₹{}, TxnId={}",
                    event.customerEmail(), event.amount(), event.transactionId());
        } catch (Exception e) {
            log.error("[NOTIFICATION] Error deserializing payment.success event: {}", e.getMessage());
        }
    }

    @KafkaListener(
            topics = "#{T(com.fooddelivery.backend.config.KafkaConfig).TOPIC_ORDER_STATUS_CHANGED}",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderStatusChanged(String message) {
        try {
            OrderEvents.OrderStatusChangedEvent event = objectMapper.readValue(message, OrderEvents.OrderStatusChangedEvent.class);
            String notification = switch (event.newStatus()) {
                case ACCEPTED  -> "✅ Your order has been accepted by " + event.restaurantName() + "!";
                case PREPARING -> "👨‍🍳 " + event.restaurantName() + " is preparing your food.";
                case READY     -> "🛵 Your order is ready! Assigning a delivery partner...";
                case PICKED_UP -> "🚴 Your order is on the way!";
                case DELIVERED -> "🎉 Order delivered! Enjoy your meal. Please rate your experience.";
                case CANCELLED -> "❌ Your order has been cancelled.";
                default        -> "Order status updated: " + event.newStatus();
            };
            log.info("[NOTIFICATION] {} → Email={}, OrderId={}", notification, event.customerEmail(), event.orderId());
        } catch (Exception e) {
            log.error("[NOTIFICATION] Error deserializing order.status.changed event: {}", e.getMessage());
        }
    }

    @KafkaListener(
            topics = "#{T(com.fooddelivery.backend.config.KafkaConfig).TOPIC_ORDER_DELIVERED}",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderDelivered(String message) {
        try {
            OrderEvents.OrderDeliveredEvent event = objectMapper.readValue(message, OrderEvents.OrderDeliveredEvent.class);
            log.info("[NOTIFICATION] ⭐ Delivery complete! Sending review prompt to {} for order {}",
                    event.customerEmail(), event.orderId());
        } catch (Exception e) {
            log.error("[NOTIFICATION] Error deserializing order.delivered event: {}", e.getMessage());
        }
    }
}
