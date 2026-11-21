package com.fooddelivery.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/** "4.3 ★ from 1,204 reviews" + how many of each star (for the bar chart). */
@Data
@Builder
public class RatingSummaryResponse {
    private UUID restaurantId;
    private Double avgRating;
    private Integer totalReviews;
    private Map<Integer, Long> distribution; // 5 → 800, 4 → 250, ... always all 5 keys
}
