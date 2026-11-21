package com.fooddelivery.backend.exception;

import lombok.Getter;

/** HTTP 429 — slow down. retryAfterSeconds goes into the Retry-After header. */
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
