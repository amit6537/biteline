package com.fooddelivery.backend.dto.request;

import com.fooddelivery.backend.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * RegisterRequest DTO — what the client sends to register.
 *
 * CONCEPT: DTO (Data Transfer Object)
 * ─────────────────────────────────────
 * DTOs are plain objects used to carry data between layers.
 * We NEVER expose our JPA Entity (User.java) directly in
 * API requests/responses because:
 *   1. The entity has fields we don't want to expose (passwordHash)
 *   2. The API contract and DB schema should evolve independently
 *   3. We can add request-specific validation here without
 *      polluting the entity
 *
 * CONCEPT: Bean Validation (@NotBlank, @Email, @Size, ...)
 * ──────────────────────────────────────────────────────────
 * Spring validates the incoming JSON against these annotations
 * when the controller uses @Valid. If validation fails, Spring
 * automatically returns a 400 Bad Request with error details.
 * No if-else validation code needed in the service layer.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be 2-100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Must be a valid 10-digit Indian phone number")
    private String phone;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    // Defaults to CUSTOMER if not provided
    private Role role = Role.CUSTOMER;
}

