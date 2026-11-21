package com.fooddelivery.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Admin dashboard row: a READY order that nobody is delivering. */
@Data
@Builder
public class StuckOrderResponse {
    private UUID orderId;
    private UUID restaurantId;
    private String restaurantName;
    private Double restaurantLatitude;
    private Double restaurantLongitude;
    private LocalDateTime readyAt;
    private long waitingMinutes;
    private boolean alerted;
}
