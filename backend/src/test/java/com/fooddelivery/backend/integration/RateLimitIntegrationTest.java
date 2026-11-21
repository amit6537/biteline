package com.fooddelivery.backend.integration;

import com.fooddelivery.backend.config.RateLimitProperties;
import com.fooddelivery.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Step 4: login brute-force protection + per-IP limit on auth endpoints. */
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RateLimitProperties props;

    private int originalIpLimit;

    @AfterEach
    void restoreLimits() {
        if (originalIpLimit > 0) {
            props.setAuthPerIpLimit(originalIpLimit);
        }
        redis.delete("rl:auth-ip:127.0.0.1"); // don't leak counters into other tests
    }

    private int tryLogin(String email, String password) {
        return api.post("/api/auth/login", Map.of("email", email, "password", password)).status();
    }

    @Test
    @DisplayName("5 wrong passwords → locked, even the right password gets 429")
    void bruteForceLocksTheAccount() {
        Account a = register("CUSTOMER", "Brute Target");
        for (int i = 0; i < 5; i++) {
            assertThat(tryLogin(a.email(), "wrong-" + i)).isEqualTo(401);
        }
        var locked = api.post("/api/auth/login", Map.of("email", a.email(), "password", "password123"));
        assertThat(locked.status()).isEqualTo(429);
        assertThat(locked.text("message")).contains("Too many failed login attempts");

        // Email case doesn't give a fresh counter
        assertThat(tryLogin(a.email().toUpperCase(), "password123")).isEqualTo(429);

        // Other accounts are not affected
        Account other = register("CUSTOMER", "Innocent Bystander");
        assertThat(tryLogin(other.email(), "password123")).isEqualTo(200);
    }

    @Test
    @DisplayName("A successful login forgives earlier mistakes from that IP")
    void successResetsTheCounter() {
        Account a = register("CUSTOMER", "Clumsy Typer");
        for (int i = 0; i < 4; i++) {
            tryLogin(a.email(), "typo");
        }
        assertThat(tryLogin(a.email(), "password123")).isEqualTo(200);
        for (int i = 0; i < 4; i++) {
            assertThat(tryLogin(a.email(), "typo")).isEqualTo(401); // not 429
        }
    }

    @Test
    @DisplayName("Attack on one account from many IPs → account-wide lock")
    void distributedAttackLocksTheAccount() {
        Account a = register("CUSTOMER", "Popular Target");
        // pretend 20 failures already came from other machines
        redis.opsForValue().set("rl:login-fail:" + a.email(), "20", java.time.Duration.ofMinutes(5));
        assertThat(tryLogin(a.email(), "password123")).isEqualTo(429);
    }

    @Test
    @DisplayName("Too many auth requests from one IP → 429 with Retry-After")
    void perIpLimit() throws Exception {
        originalIpLimit = props.getAuthPerIpLimit();
        props.setAuthPerIpLimit(3);
        redis.delete("rl:auth-ip:127.0.0.1");

        for (int i = 0; i < 3; i++) {
            assertThat(tryLogin("nobody" + i + "@t.com", "x")).isEqualTo(401);
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(429);
        assertThat(Long.parseLong(res.headers().firstValue("Retry-After").orElseThrow())).isBetween(1L, 60L);

        // Non-auth endpoints are not limited
        assertThat(api.get("/api/restaurants?city=Nowhere").status()).isEqualTo(200);
    }
}
