package com.fooddelivery.backend.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateRestaurantRequest {

    @Size(max = 150)
    private String name;

    private String description;
    private String cuisineType;
    private String imageUrl;
    private Boolean isOpen;

    @DecimalMin(value = "0.5") @DecimalMax(value = "50.0")
    private Double deliveryRadiusKm;

    @DecimalMin(value = "0.0")
    private BigDecimal minOrderAmount;

    @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double latitude;

    @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double longitude;

    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(180)
    private Integer avgPrepMinutes;
}

