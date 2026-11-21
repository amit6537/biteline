package com.fooddelivery.backend.dto.response;

import com.fooddelivery.backend.entity.DeliveryAssignmentStatus;
import com.fooddelivery.backend.entity.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Everything a partner's app needs for one job:
 * where to pick up (restaurant), where to drop (customer), and its state.
 */
@Data
@Builder
public class DeliveryAssignmentResponse {
    private UUID id;
    private UUID orderId;
    private OrderStatus orderStatus;
    private DeliveryAssignmentStatus status;

    private UUID partnerId;
    private String partnerName;

    private String restaurantName;
    private Double restaurantLatitude;
    private Double restaurantLongitude;

    private String customerName;
    private String deliveryAddressText;
    private BigDecimal orderTotal;

    private LocalDateTime assignedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
}
