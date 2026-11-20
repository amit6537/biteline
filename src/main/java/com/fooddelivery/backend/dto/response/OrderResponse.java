package com.fooddelivery.backend.dto.response;

import com.fooddelivery.backend.entity.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OrderResponse {
    private UUID id;
    private UUID customerId;
    private String customerName;
    private UUID restaurantId;
    private String restaurantName;
    private UUID deliveryAddressId;
    private String deliveryAddressText;
    private OrderStatus status;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal totalAmount;
    private String specialInstructions;
    private List<OrderItemResponse> items;
    private LocalDateTime placedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime readyAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
    private LocalDateTime estimatedDeliveryAt;
}

