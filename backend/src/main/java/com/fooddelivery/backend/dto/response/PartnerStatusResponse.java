package com.fooddelivery.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PartnerStatusResponse {
    private UUID partnerId;
    @Getter(onMethod_ = @JsonProperty("isOnline")) // partner's choice: wants orders?
    private boolean isOnline;
    @Getter(onMethod_ = @JsonProperty("isAvailable")) // online AND not busy right now
    private boolean isAvailable;
    private Double latitude;
    private Double longitude;
    private LocalDateTime lastSeenAt;
    private UUID currentAssignmentId; // null when free
}
