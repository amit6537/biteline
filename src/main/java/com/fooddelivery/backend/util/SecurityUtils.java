package com.fooddelivery.backend.util;

import com.fooddelivery.backend.entity.User;
import com.fooddelivery.backend.exception.ResourceNotFoundException;
import com.fooddelivery.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * SecurityUtils — helpers to access the currently authenticated user.
 *
 * CONCEPT: SecurityContextHolder
 * ────────────────────────────────
 * After JwtFilter runs, the authenticated user is stored in:
 *   SecurityContextHolder → SecurityContext → Authentication
 *
 * Authentication.getName() returns the "principal" — in our case,
 * the email we set as the JWT subject ("sub" claim).
 *
 * Any component (service, controller) can call this to find out
 * WHO is making the current request — without passing the user
 * object down through method parameters.
 *
 * WHY a separate utility class?
 *   Services would otherwise need to call SecurityContextHolder directly
 *   (tight coupling to Spring Security) and UserRepository (two concerns).
 *   This class encapsulates both and provides a clean one-liner:
 *     User me = securityUtils.getCurrentUser();
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;

    /**
     * Returns the email of the currently authenticated user.
     * This is the JWT "sub" claim — set in JwtFilter.
     */
    public String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in context");
        }
        return auth.getName(); // = email (the JWT subject)
    }

    /**
     * Loads the full User entity for the authenticated user.
     * Throws 404 if somehow the email in the token doesn't match any DB user.
     */
    public User getCurrentUser() {
        String email = getCurrentUserEmail();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user not found: " + email));
    }

    /**
     * Returns the UUID of the authenticated user — without loading the full entity.
     * Use this when you only need the ID (e.g. for a FK assignment).
     */
    public java.util.UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }
}

