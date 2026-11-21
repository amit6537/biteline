package com.fooddelivery.backend.integration;

import com.fooddelivery.backend.support.AbstractIntegrationTest;
import com.fooddelivery.backend.support.Api;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Forgot password. The raw token only travels by email, and the DB keeps its
 * hash, so tests plant a token with a KNOWN value straight into the table.
 */
class PasswordResetIntegrationTest extends AbstractIntegrationTest {

    private Api.Response forgot(String email) {
        return api.post("/api/auth/forgot-password", Map.of("email", email));
    }

    private Api.Response reset(String token, String newPassword) {
        return api.post("/api/auth/reset-password", Map.of("token", token, "newPassword", newPassword));
    }

    private int openTokens(String userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?::uuid AND used_at IS NULL",
                Integer.class, userId);
    }

    /** Insert a token whose raw value we know. minutesLeft < 0 = already expired. */
    private String plantToken(String userId, int minutesLeft) throws Exception {
        String token = "test-" + UUID.randomUUID();
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        jdbc.update("INSERT INTO password_reset_tokens (id, user_id, token_hash, expires_at) "
                        + "VALUES (?::uuid, ?::uuid, ?, NOW() + make_interval(mins => ?))",
                UUID.randomUUID().toString(), userId, hash, minutesLeft);
        return token;
    }

    @Test
    @DisplayName("Same answer for real and unknown emails; a token is created only for the real one")
    void forgotDoesNotRevealAccounts() {
        Account a = register("CUSTOMER", "Forgetful User");

        Api.Response known = forgot(a.email());
        Api.Response unknown = forgot("nobody-" + UUID.randomUUID() + "@example.com");

        assertThat(known.status()).isEqualTo(202);
        assertThat(unknown.status()).isEqualTo(202);
        assertThat(known.text("message")).isEqualTo(unknown.text("message"));
        assertThat(openTokens(a.userId())).isEqualTo(1);

        // the DB never holds the raw token: only a 64-char hex hash
        String stored = jdbc.queryForObject("SELECT token_hash FROM password_reset_tokens WHERE user_id = ?::uuid",
                String.class, a.userId());
        assertThat(stored).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("A new request replaces the old link")
    void onlyNewestLinkWorks() {
        Account a = register("CUSTOMER", "Two Requests");
        forgot(a.email());
        forgot(a.email());
        assertThat(openTokens(a.userId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Reset sets the new password, works once, and logs out every device")
    void resetChangesPasswordOnceAndLogsOut() throws Exception {
        Account a = register("CUSTOMER", "Reset User");
        String token = plantToken(a.userId(), 30);

        assertThat(reset(token, "brand-new-pass").status()).isEqualTo(204);

        // old password dead, new one works
        assertThat(api.post("/api/auth/login", Map.of("email", a.email(), "password", "password123")).status())
                .isEqualTo(401);
        assertThat(login(a.email(), "brand-new-pass").token()).isNotBlank();

        // the session from before the reset is logged out
        assertThat(api.post("/api/auth/refresh", Map.of("refreshToken", a.refreshToken())).status()).isEqualTo(401);

        // the link can't be used a second time
        assertThat(reset(token, "another-pass-1").status()).isEqualTo(400);
    }

    @Test
    void expiredOrUnknownTokensAreRejected() throws Exception {
        Account a = register("CUSTOMER", "Late User");
        String expired = plantToken(a.userId(), -1);

        Api.Response r = reset(expired, "brand-new-pass");
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.text("message")).contains("invalid or has expired");
        assertThat(reset("made-up-token", "brand-new-pass").status()).isEqualTo(400);

        // password unchanged
        assertThat(login(a.email(), "password123").token()).isNotBlank();
    }

    @Test
    void newPasswordMustBeLongEnough() throws Exception {
        Account a = register("CUSTOMER", "Short Pass");
        String token = plantToken(a.userId(), 30);

        assertThat(reset(token, "short").status()).isEqualTo(400);
        assertThat(reset(token, "long-enough-pass").status()).isEqualTo(204); // token not burned by the bad try
    }
}
