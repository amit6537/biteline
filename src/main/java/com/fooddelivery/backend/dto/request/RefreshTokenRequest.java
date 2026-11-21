package com.fooddelivery.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Body of POST /api/auth/refresh and POST /api/auth/logout. */
@Data
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
