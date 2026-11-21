package com.fooddelivery.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

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
    @Getter(onMethod_ = @JsonProperty("isAvailable")) // JSON name keeps the "is" prefix (Lombok getter would drop it)
    private boolean isAvailable;
    private String imageUrl;
}

