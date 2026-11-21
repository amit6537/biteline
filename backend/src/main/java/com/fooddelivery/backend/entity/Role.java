package com.fooddelivery.backend.entity;

/**
 * Role enum — defines what a user can do in the system.
 *
 * CONCEPT: Role-Based Access Control (RBAC)
 * ──────────────────────────────────────────
 * Instead of checking "is this user ID allowed?" on every endpoint,
 * we assign roles and check roles. Much cleaner.
 *
 * In Spring Security, roles are prefixed with "ROLE_" internally.
 * So our CUSTOMER becomes "ROLE_CUSTOMER" in the security context.
 * That's why @PreAuthorize("hasRole('CUSTOMER')") works — Spring
 * automatically prepends "ROLE_" when matching.
 */
public enum Role {
    CUSTOMER,
    RESTAURANT_OWNER,
    DELIVERY_PARTNER,
    ADMIN
}

