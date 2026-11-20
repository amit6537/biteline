package com.fooddelivery.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class CartItemResponse {
    private UUID id;
    private UUID menuItemId;
    private String menuItemName;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal itemTotal;
    private boolean isAvailable;
    private String imageUrl;
}

