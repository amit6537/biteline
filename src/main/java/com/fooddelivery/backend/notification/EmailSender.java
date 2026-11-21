package com.fooddelivery.backend.notification;

/**
 * EmailSender — "send one email". Two implementations:
 *   SmtpEmailSender → real email through an SMTP server (app.email.enabled=true)
 *   LogEmailSender  → just logs it                       (app.email.enabled=false)
 *
 * CONCEPT: Strategy pattern + @ConditionalOnProperty
 *   Exactly one of the two classes becomes a bean, decided by a config value.
 *   EmailDispatcher only knows this interface, never which one it got.
 */
public interface EmailSender {

    /** Throws on failure — the dispatcher catches it and schedules a retry. */
    void send(String to, String subject, String body);
}
