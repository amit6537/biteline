package com.fooddelivery.backend.dto.response;

import com.fooddelivery.backend.entity.PaymentMethod;
import com.fooddelivery.backend.entity.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PaymentResponse {
    private UUID id;
    private UUID orderId;
    private PaymentStatus status;
    private BigDecimal amount;
    private PaymentMethod method;
    private String transactionId;
    private String idempotencyKey;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}

