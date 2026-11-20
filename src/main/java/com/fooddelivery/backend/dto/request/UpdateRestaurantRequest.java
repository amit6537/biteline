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

    @DecimalMin(value = "0.0")
    private Double deliveryRadiusKm;

    @DecimalMin(value = "0.0")
    private BigDecimal minOrderAmount;

    private Double latitude;
    private Double longitude;
}

