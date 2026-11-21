package com.fooddelivery.backend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Sent by the partner app: "I'm online/offline" and/or "I'm here now".
 *
 * available is optional: the app pings its location every few seconds
 * during a delivery WITHOUT changing availability (send only lat/lng).
 */
@Data
public class UpdatePartnerStatusRequest {

    private Boolean available; // null = don't change

    @NotNull(message = "Latitude is required")
    @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double longitude;
}
