package com.fooddelivery.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PartnerRatingResponse {
    private UUID partnerId;
    private Double avgDeliveryRating; // null until the first rating
    private Long totalRatings;
}
