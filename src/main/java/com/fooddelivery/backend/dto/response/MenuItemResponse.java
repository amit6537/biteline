package com.fooddelivery.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class MenuItemResponse {
    private UUID id;
    private UUID restaurantId;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private String imageUrl;
    private boolean isAvailable;
    private boolean isVegetarian;
}

