package com.fooddelivery.backend.dto.response;

import com.fooddelivery.backend.entity.Role;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * AuthResponse DTO — what we send back after login/register.
 *
 * CONCEPT: What goes in a JWT response?
 * ────────────────────────────────────────
 * accessToken  → Short-lived token (24h). Used in every API call.
 *                Client sends: Authorization: Bearer <accessToken>
 *
 * refreshToken → Long-lived token (7 days). ONLY used to get a
 *                new accessToken when it expires. Never sent to
 *                regular API endpoints.
 *
 * WHY two tokens?
 *   If we made the accessToken last 7 days, a leaked token gives
 *   an attacker a week of access. With 24h access + 7d refresh:
 *   - Leaked accessToken → attacker has max 24h
 *   - Stolen refreshToken → we can blacklist it in Redis (Phase 9)
 *
 * We also return user info so the client doesn't need a separate
 * "get current user" API call right after login.
 */
@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;       // Always "Bearer"
    private long expiresIn;         // seconds until accessToken expires
    private UUID userId;
    private String name;
    private String email;
    private Role role;
}

