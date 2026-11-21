package com.fooddelivery.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor  // Jackson needs an empty constructor to read it back from Redis
@AllArgsConstructor // @Builder needs this once @NoArgsConstructor exists
public class MenuItemResponse {
    private UUID id;
    private UUID restaurantId;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private String imageUrl;
    @Getter(onMethod_ = @JsonProperty("isAvailable")) // JSON name keeps the "is" prefix (Lombok getter would drop it)
    private boolean isAvailable;
    @Getter(onMethod_ = @JsonProperty("isVegetarian")) // JSON name keeps the "is" prefix (Lombok getter would drop it)
    private boolean isVegetarian;
}

