package com.fooddelivery.backend.service;

import com.fooddelivery.backend.config.RateLimitProperties;
import com.fooddelivery.backend.entity.PasswordResetToken;
import com.fooddelivery.backend.entity.User;
import com.fooddelivery.backend.exception.BadRequestException;
import com.fooddelivery.backend.notification.EmailSender;
import com.fooddelivery.backend.repository.PasswordResetTokenRepository;
import com.fooddelivery.backend.repository.UserRepository;
import com.fooddelivery.backend.util.RateLimiter;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PasswordResetService — "Forgot password?".
 *
 * Flow:
 *   1. POST /api/auth/forgot-password {email}
 *      → if the account exists, we email a link:
 *        http://localhost:5173/reset-password?token=<random>
 *      → the answer is ALWAYS the same 202, account or not. Otherwise
 *        anyone could type emails and learn who has an account here
 *        ("user enumeration").
 *   2. POST /api/auth/reset-password {token, newPassword}
 *      → token valid, unused, not expired → new password saved,
 *        every device logged out, login lock cleared.
 *
 * CONCEPT: store a HASH of the token
 * ───────────────────────────────────
 * The token is as good as a password for 30 minutes. Like passwords, we
 * keep only a hash. SHA-256 (not BCrypt) is enough here because the token
 * is 256 random bits — nobody can guess it, so slow hashing adds nothing.
 */
@Service
@Slf4j
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final EmailSender emailSender;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProps;
    private final String frontendUrl;
    private final Duration tokenTtl;
    private final int perEmailLimit;
    private final Duration perEmailWindow;

    /**
     * Emails are sent on a background thread: SMTP can take seconds, and a
     * reply that is slower for real accounts than for unknown emails would
     * leak who is registered (a "timing attack").
     */
    private final ExecutorService mailThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "password-reset-mail");
        t.setDaemon(true);
        return t;
    });

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                RefreshTokenService refreshTokenService,
                                LoginAttemptService loginAttemptService,
                                EmailSender emailSender,
                                RateLimiter rateLimiter,
                                RateLimitProperties rateLimitProps,
                                @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
                                @Value("${app.auth.password-reset-ttl:PT30M}") Duration tokenTtl,
                                @Value("${app.auth.password-reset-per-email-limit:3}") int perEmailLimit,
                                @Value("${app.auth.password-reset-per-email-window:PT15M}") Duration perEmailWindow) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
        this.rateLimitProps = rateLimitProps;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.tokenTtl = tokenTtl;
        this.perEmailLimit = perEmailLimit;
        this.perEmailWindow = perEmailWindow;
    }

    @PreDestroy
    void shutdown() {
        mailThread.shutdown();
    }

    /** Step 1. Never throws for an unknown email — the caller always gets the same answer. */
    @Transactional
    public void requestReset(String rawEmail) {
        String email = rawEmail.trim();

        // Max 3 emails per address per 15 min: stops someone flooding a victim's inbox.
        // Over the limit we quietly do nothing (same answer as always).
        if (rateLimitProps.isEnabled()) {
            String key = "pwd-reset:" + email.toLowerCase(Locale.ROOT);
            if (!rateLimiter.hit(key, perEmailLimit, perEmailWindow).allowed()) {
                log.warn("[AUTH] Password reset limit reached for {}", email);
                return;
            }
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.info("[AUTH] Password reset requested for unknown email");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        tokenRepository.invalidateAllForUser(user.getId(), now); // only the newest link works

        String token = newToken();
        tokenRepository.save(PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .tokenHash(sha256(token))
                .createdAt(now)
                .expiresAt(now.plus(tokenTtl))
                .build());

        String link = frontendUrl + "/reset-password?token=" + token;
        String to = user.getEmail();
        String body = """
                Hi %s,

                Someone (hopefully you) asked to reset the password of your Food Delivery account.

                Choose a new password here (the link works once, for %d minutes):
                %s

                Didn't ask for this? Ignore this email. Your password stays the same.

                — Food Delivery""".formatted(user.getName(), tokenTtl.toMinutes(), link);

        // Send only AFTER the row is committed — otherwise the link could
        // arrive before the token exists (or for a token that was rolled back).
        afterCommit(() -> mailThread.execute(() -> {
            try {
                emailSender.send(to, "Reset your Food Delivery password", body);
                log.info("[AUTH] Password reset email sent to {}", to);
            } catch (Exception e) {
                log.error("[AUTH] Could not send password reset email to {}: {}", to, e.getMessage());
            }
        }));
    }

    /** Step 2. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken t = tokenRepository.findByTokenHashForUpdate(sha256(token.trim()))
                .filter(x -> x.getUsedAt() == null && x.getExpiresAt().isAfter(now))
                // one message for unknown / used / expired: nothing to learn from it
                .orElseThrow(() -> new BadRequestException(
                        "This reset link is invalid or has expired. Please request a new one."));

        User user = userRepository.findById(t.getUserId())
                .orElseThrow(() -> new BadRequestException("This reset link is invalid or has expired."));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepository.invalidateAllForUser(user.getId(), now); // this link + any other open one
        int sessions = refreshTokenService.logoutAll(user.getId()); // the old password may have leaked
        loginAttemptService.clearAccount(user.getEmail());        // the owner proved it's them

        log.info("[AUTH] Password reset for {} ({} session(s) logged out)", user.getEmail(), sessions);
    }

    // ── helpers ──────────────────────────────────────────────

    /** 32 random bytes → 43 URL-safe characters. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every JVM has SHA-256
        }
    }

    private static void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
