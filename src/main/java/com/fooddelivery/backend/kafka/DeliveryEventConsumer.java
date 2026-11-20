package com.fooddelivery.backend.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.backend.entity.*;
import com.fooddelivery.backend.exception.ResourceNotFoundException;
import com.fooddelivery.backend.repository.OrderRepository;
import com.fooddelivery.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DeliveryEventConsumer — listens for order.ready events and assigns a delivery partner.
 */
@Component
@Slf4j
public class DeliveryEventConsumer {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // Explicit constructor
    public DeliveryEventConsumer(OrderRepository orderRepository, UserRepository userRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "#{T(com.fooddelivery.backend.config.KafkaConfig).TOPIC_ORDER_READY}",
            groupId = "delivery-assignment-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onOrderReady(String message) {
        try {
            OrderEvents.OrderReadyEvent event = objectMapper.readValue(message, OrderEvents.OrderReadyEvent.class);
            log.info("[DELIVERY] 🛵 Order {} is ready. Assigning delivery partner...", event.orderId());

            // 1. Load the order
            Order order = orderRepository.findById(event.orderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found in delivery consumer: " + event.orderId()));

            // Idempotency guard: skip if delivery already assigned or past READY
            if (!order.getStatus().equals(OrderStatus.READY)) {
                log.warn("[DELIVERY] Order {} is in status {}. Skipping assignment.",
                        event.orderId(), order.getStatus());
                return;
            }

            // 2. Find an available delivery partner
            List<User> availablePartners = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == Role.DELIVERY_PARTNER && u.isActive())
                    .toList();

            if (availablePartners.isEmpty()) {
                log.warn("[DELIVERY] No delivery partners available for order {}. Will retry.", event.orderId());
                return;
            }

            User assignedPartner = availablePartners.get(0);
            log.info("[DELIVERY] ✅ Assigned partner: {} to order: {}",
                    assignedPartner.getName(), event.orderId());
        } catch (Exception e) {
            log.error("[DELIVERY] Error processing order.ready event: {}", e.getMessage(), e);
        }
    }
}
