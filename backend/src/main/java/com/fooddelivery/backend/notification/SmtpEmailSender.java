package com.fooddelivery.backend.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SmtpEmailSender — sends real email via Spring's JavaMailSender.
 *
 * Locally it talks to Mailpit (docker-compose) → read mails at
 * http://localhost:8025. In production point spring.mail.* at SES,
 * SendGrid, Gmail SMTP, etc. — no code change.
 */
@Component
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    @Value("${app.email.from:no-reply@fooddelivery.local}")
    private String from;

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message); // throws MailException on failure → retried
        log.debug("[EMAIL] sent '{}' to {}", subject, to);
    }
}
