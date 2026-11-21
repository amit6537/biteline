package com.fooddelivery.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ReviewResponse {
    private UUID id;
    private UUID orderId;
    private UUID restaurantId;
    private String reviewerName;   // "Amit S." — never the full name or email
    private int foodRating;
    private Integer deliveryRating;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean edited;
}
