package com.fooddelivery.backend.entity;

/**
 * Email side of a notification (the "outbox" state).
 *   PENDING → waiting to be sent (or waiting for its next retry)
 *   SENT    → delivered to the mail server
 *   FAILED  → gave up after max attempts
 *   SKIPPED → never needs an email (e.g. rows created before email existed)
 */
public enum EmailStatus {
    PENDING, SENT, FAILED, SKIPPED
}
