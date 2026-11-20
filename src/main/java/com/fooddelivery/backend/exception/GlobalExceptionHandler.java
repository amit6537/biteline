package com.fooddelivery.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler — central place for all error handling.
 *
 * CONCEPT: @RestControllerAdvice
 * ───────────────────────────────
 * @RestControllerAdvice is AOP (Aspect-Oriented Programming) in action.
 * It intercepts exceptions thrown from ANY @RestController in the app.
 *
 * Without this, Spring would return a generic "500 Internal Server Error"
 * or a Whitelabel Error Page for every unhandled exception.
 *
 * With this, we:
 *   1. Control the response format (consistent JSON error shape)
 *   2. Control the HTTP status code
 *   3. Log at the right level (don't log 404 as ERROR)
 *   4. Never leak internal details (stack traces, SQL) to clients
 *
 * CONCEPT: Error Response Shape
 * ───────────────────────────────
 * Our error response always looks like:
 * {
 *   "timestamp": "2024-01-01T10:00:00",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Email is already registered",
 *   "fieldErrors": { "email": "Must be a valid email" }  // only for validation errors
 * }
 *
 * Consistent error shapes make the frontend much easier to build.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── Validation errors (400) ───────────────────────────────

    /**
     * Triggered when @Valid fails on a request DTO.
     * Collects all field-level errors and returns them in one response.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationError(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError err : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(err.getField(), err.getDefaultMessage());
        }

        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    // ── Custom business exceptions ────────────────────────────

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), null);
    }

    // ── Spring Security exceptions (401) ──────────────────────

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid email or password", null);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, Object>> handleDisabled(DisabledException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Account is disabled", null);
    }

    // ── Catch-all (500) ───────────────────────────────────────

    /**
     * Last-resort handler. Log the full stack trace (we want to know about these),
     * but return a generic message to the client (never leak internals).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.", null);
    }

    // ── Helper ────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, String message, Map<String, String> fieldErrors) {

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            body.put("fieldErrors", fieldErrors);
        }
        return ResponseEntity.status(status).body(body);
    }
}

