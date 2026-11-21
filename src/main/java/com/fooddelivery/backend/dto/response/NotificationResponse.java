package com.fooddelivery.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fooddelivery.backend.entity.NotificationType;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private NotificationType type;
    private String title;
    private String message;
    private UUID orderId; // lets the app open the right order screen on tap
    @Getter(onMethod_ = @JsonProperty("isRead")) // JSON name keeps the "is" prefix
    private boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
