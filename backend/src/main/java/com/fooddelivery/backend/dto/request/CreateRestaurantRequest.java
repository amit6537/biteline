package com.fooddelivery.backend.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateRestaurantRequest {

    @NotBlank(message = "Restaurant name is required")
    @Size(max = 150)
    private String name;

    private String description;

    private String cuisineType;

    private String imageUrl;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
    private Double longitude;

    @NotBlank(message = "City is required")
    private String city;

    @DecimalMin(value = "0.5", message = "Delivery radius must be at least 0.5 km")
    @DecimalMax(value = "50.0", message = "Delivery radius can be at most 50 km")
    private Double deliveryRadiusKm = 5.0;

    // Phase 12: typical kitchen time in minutes (used for ETA)
    @jakarta.validation.constraints.Min(value = 1, message = "Prep time must be 1-180 minutes")
    @jakarta.validation.constraints.Max(value = 180, message = "Prep time must be 1-180 minutes")
    private Integer avgPrepMinutes = 20;

    @DecimalMin(value = "0.0", message = "Min order amount must be positive")
    private BigDecimal minOrderAmount = BigDecimal.ZERO;
}

