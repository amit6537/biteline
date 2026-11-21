package com.fooddelivery.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * ConflictException — thrown when a unique constraint is violated.
 * Maps to HTTP 409 Conflict (e.g. duplicate email on register).
 *
 * CONCEPT: Custom Exceptions with @ResponseStatus
 * ──────────────────────────────────────────────────
 * Instead of manually building ResponseEntity with error codes
 * everywhere, we throw typed exceptions. The GlobalExceptionHandler
 * catches them and maps to the right HTTP status.
 *
 * This keeps service layer code clean — no HTTP awareness needed.
 * "throw new ConflictException(...)" is readable and testable.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}

