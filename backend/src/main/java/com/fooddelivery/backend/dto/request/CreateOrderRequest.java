package com.fooddelivery.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateOrderRequest {

    @NotNull(message = "Delivery address ID is required")
    private UUID deliveryAddressId;

    private String specialInstructions;
}

