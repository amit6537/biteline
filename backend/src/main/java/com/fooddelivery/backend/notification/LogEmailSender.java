package com.fooddelivery.backend.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LogEmailSender — used when app.email.enabled=false (or not set):
 * writes the email to the log instead of sending it. Handy for tests/CI.
 */
@Component
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class LogEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[EMAIL-LOG] 📧 to={} | {} | {}", to, subject, body);
    }
}
