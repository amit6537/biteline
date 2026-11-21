package com.fooddelivery.backend.dto.response;

import com.fooddelivery.backend.entity.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Live tracking screen: the map (restaurant, customer, rider) + ETA.
 * partner* fields are null unless a rider is currently on this order.
 */
@Data
@Builder
public class TrackingResponse {
    private UUID orderId;
    private OrderStatus status;
    private LocalDateTime estimatedDeliveryAt;
    private Long etaMinutes;               // null once delivered/cancelled

    private String restaurantName;
    private Double restaurantLatitude;
    private Double restaurantLongitude;

    private Double deliveryLatitude;       // null if the address had no coordinates
    private Double deliveryLongitude;

    private String partnerName;
    private Double partnerLatitude;
    private Double partnerLongitude;
    private LocalDateTime partnerLastSeenAt;
    private Boolean partnerLocationStale;  // true = phone hasn't pinged recently
    private Double partnerDistanceKm;      // to restaurant (before pickup) or to customer (after)
}
