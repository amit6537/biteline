package com.fooddelivery.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AdminAssignRequest {
    @NotNull(message = "partnerId is required")
    private UUID partnerId;
}
