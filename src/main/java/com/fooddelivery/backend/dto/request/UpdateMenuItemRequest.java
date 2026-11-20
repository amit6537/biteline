package com.fooddelivery.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateMenuItemRequest {

    @Size(max = 150)
    private String name;

    private String description;

    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    private String category;
    private String imageUrl;
    private Boolean isAvailable;
    private Boolean isVegetarian;
}

