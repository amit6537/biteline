package com.fooddelivery.backend.dto.request;

import com.fooddelivery.backend.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ProcessPaymentRequest {

    @NotNull(message = "Order ID is required")
    private UUID orderId;

    @NotNull(message = "Payment method is required")
    private PaymentMethod method;

    // Client-supplied idempotency key (UUID or client-generated unique string)
    private String idempotencyKey;
}

