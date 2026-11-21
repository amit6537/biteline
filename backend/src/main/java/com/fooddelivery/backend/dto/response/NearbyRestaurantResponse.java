package com.fooddelivery.backend.dto.response;

import lombok.Builder;
import lombok.Data;

/** One "near me" result: the restaurant + how far + roughly how long. */
@Data
@Builder
public class NearbyRestaurantResponse {
    private RestaurantResponse restaurant;
    private double distanceKm;
    private long estimatedDeliveryMinutes; // prep + partner search + travel
}
