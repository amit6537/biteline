package com.fooddelivery.backend.controller;

import com.fooddelivery.backend.dto.request.LoginRequest;
import com.fooddelivery.backend.dto.request.RefreshTokenRequest;
import com.fooddelivery.backend.dto.request.RegisterRequest;
import com.fooddelivery.backend.dto.response.AuthResponse;
import com.fooddelivery.backend.service.AuthService;
import com.fooddelivery.backend.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthController — HTTP layer for authentication endpoints.
 *
 * CONCEPT: Controller Responsibility
 * ─────────────────────────────────────
 * Controllers should be THIN — they only:
 *   1. Receive HTTP input (@RequestBody, @PathVariable, etc.)
 *   2. Call the service
 *   3. Return an HTTP response
 *
 * No business logic here. No DB calls here. Just HTTP.
 *
 * CONCEPT: @Valid
 * ────────────────
 * @Valid triggers Bean Validation on the DTO before the method body runs.
 * If the DTO has validation errors (@NotBlank, @Email etc.),
 * Spring returns a 400 response automatically with error details.
 * Our GlobalExceptionHandler formats that into a clean JSON response.
 *
 * CONCEPT: ResponseEntity<T>
 * ────────────────────────────
 * ResponseEntity lets us control the full HTTP response:
 *   - Status code (201 Created, 200 OK, etc.)
 *   - Headers
 *   - Body
 *
 * WHY 201 for register, 200 for login?
 *   HTTP 201 = "Created" — a new resource was created.
 *   HTTP 200 = "OK" — an existing resource was returned.
 *   Following HTTP semantics makes the API self-documenting.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SecurityUtils securityUtils;

    /**
     * POST /api/auth/register
     * Open to everyone — no JWT required (configured in SecurityConfig).
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    /**
     * POST /api/auth/login
     * Open to everyone — no JWT required.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // getRemoteAddr() = the IP that connected to us. Behind a load balancer
        // set server.forward-headers-strategy so it becomes the real client IP.
        return ResponseEntity.ok(authService.login(request, http.getRemoteAddr()));
    }

    /**
     * POST /api/auth/refresh — body {"refreshToken": "..."}
     * Old refresh token → new access token + new refresh token.
     * The frontend calls this when an API call answers 401 (access token expired).
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    /**
     * POST /api/auth/logout — body {"refreshToken": "..."}
     * Logs out this device. 204 No Content, even if the token was already invalid.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/auth/logout-all — needs a valid ACCESS token.
     * Logs out every device of the current user.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll() {
        authService.logoutAll(securityUtils.getCurrentUser());
        return ResponseEntity.noContent().build();
    }
}
