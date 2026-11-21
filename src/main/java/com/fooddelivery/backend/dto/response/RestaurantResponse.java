package com.fooddelivery.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

/**
 * RestaurantResponse DTO — what clients receive when querying restaurants.
 *
 * CONCEPT: Response DTO Design
 * ──────────────────────────────
 * Notice we don't return the full entity:
 *   - No owner object (just ownerId) — no leaking user data
 *   - No internal DB fields (createdAt optional, updatedAt hidden)
 *   - Computed fields like isOpen returned as simple booleans
 *
 * This is the "API contract" — it can evolve independently of the DB schema.
 * Example: we could rename the DB column "is_open" → "open_now" without
 * changing this DTO or breaking clients.
 */
@Data
@Builder
@NoArgsConstructor  // Jackson needs an empty constructor to read it back from Redis
@AllArgsConstructor // @Builder needs this once @NoArgsConstructor exists
public class RestaurantResponse {
    private UUID id;
    private UUID ownerId;
    private String name;
    private String description;
    private String cuisineType;
    private String imageUrl;
    private Double avgRating;
    private Integer totalReviews;
    @Getter(onMethod_ = @JsonProperty("isOpen")) // JSON name keeps the "is" prefix (Lombok getter would drop it)
    private boolean isOpen;
    private Double deliveryRadiusKm;
    private Double minOrderAmount;
    private Double latitude;
    private Double longitude;
    private String city;
    private Integer avgPrepMinutes; // Phase 12
}

