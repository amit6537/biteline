package com.fooddelivery.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateMenuItemRequest {

    @NotBlank(message = "Item name is required")
    @Size(max = 150)
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    @NotBlank(message = "Category is required")
    private String category;

    private String imageUrl;

    @JsonProperty("isVegetarian") // keep the "is" prefix in JSON (Lombok would drop it)
    private boolean isVegetarian = false;
}

