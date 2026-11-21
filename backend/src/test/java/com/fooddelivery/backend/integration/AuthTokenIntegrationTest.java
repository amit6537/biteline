package com.fooddelivery.backend.integration;

import com.fooddelivery.backend.support.AbstractIntegrationTest;
import com.fooddelivery.backend.support.Api;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** Step 4: refresh-token rotation, reuse detection, logout. */
class AuthTokenIntegrationTest extends AbstractIntegrationTest {

    private Api.Response refresh(String refreshToken) {
        return api.post("/api/auth/refresh", Map.of("refreshToken", refreshToken));
    }

    /** Pretend a rotation happened long ago (outside the two-tabs grace window). */
    private void ageRotation(String userId) {
        jdbc.update("UPDATE refresh_tokens SET revoked_at = NOW() - INTERVAL '1 minute' "
                + "WHERE user_id = ?::uuid AND revoke_reason = 'ROTATED'", userId);
    }

    @Test
    @DisplayName("Refresh gives a working new pair; the old refresh token is used up")
    void refreshRotatesTokens() {
        Account a = register("CUSTOMER", "Refresh User");

        var r = refresh(a.refreshToken());
        assertThat(r.status()).isEqualTo(200);
        String newAccess = r.text("accessToken");
        String newRefresh = r.text("refreshToken");
        assertThat(newRefresh).isNotEqualTo(a.refreshToken());
        assertThat(r.text("userId")).isEqualTo(a.userId());

        assertThat(api.get("/api/cart", newAccess).status()).isEqualTo(200);
        assertThat(refresh(newRefresh).status()).isEqualTo(200); // the new one works once too
    }

    @Test
    @DisplayName("Stolen token reused after rotation → the whole session is logged out")
    void reuseRevokesTheFamily() {
        Account a = register("CUSTOMER", "Victim User");
        String stolen = a.refreshToken();
        String userNext = refresh(stolen).text("refreshToken"); // real user refreshes
        ageRotation(a.userId());

        var attacker = refresh(stolen);
        assertThat(attacker.status()).isEqualTo(401);
        assertThat(attacker.text("message")).contains("reuse");
        assertThat(refresh(userNext).status()).isEqualTo(401); // thief's copy AND user's copy are dead

        // other sessions (another login = another family) are not affected
        Account again = login(a.email(), "password123");
        assertThat(refresh(again.refreshToken()).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("Two tabs refreshing at once → one wins, session stays alive")
    void parallelRefreshIsNotTreatedAsTheft() {
        Account a = register("CUSTOMER", "Two Tabs");

        var t1 = CompletableFuture.supplyAsync(() -> refresh(a.refreshToken()));
        var t2 = CompletableFuture.supplyAsync(() -> refresh(a.refreshToken()));
        List<Api.Response> results = List.of(t1.join(), t2.join());

        assertThat(results).extracting(Api.Response::status).containsExactlyInAnyOrder(200, 401);
        String winner = results.stream().filter(r -> r.status() == 200).findFirst().orElseThrow()
                .text("refreshToken");
        assertThat(refresh(winner).status()).isEqualTo(200); // not logged out
    }

    @Test
    void onlyRealRefreshTokensAreAccepted() {
        Account a = register("CUSTOMER", "Wrong Token");
        assertThat(refresh(a.token()).status()).isEqualTo(401);           // an access token
        assertThat(refresh("not.a.jwt").status()).isEqualTo(401);         // garbage
        assertThat(api.post("/api/auth/refresh", Map.of()).status()).isEqualTo(400); // missing
    }

    @Test
    void deactivatedUserCannotRefresh() {
        Account a = register("CUSTOMER", "Banned Refresher");
        jdbc.update("UPDATE users SET is_active = false WHERE email = ?", a.email());
        assertThat(refresh(a.refreshToken()).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Logout kills this device's refresh token; logging out twice is fine")
    void logout() {
        Account a = register("CUSTOMER", "Logout User");
        String later = refresh(a.refreshToken()).text("refreshToken");

        assertThat(api.post("/api/auth/logout", Map.of("refreshToken", later)).status()).isEqualTo(204);
        assertThat(refresh(later).status()).isEqualTo(401);
        assertThat(api.post("/api/auth/logout", Map.of("refreshToken", later)).status()).isEqualTo(204);
        assertThat(api.post("/api/auth/logout", Map.of("refreshToken", "garbage")).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("Logout-all ends every device's session and needs an access token")
    void logoutAll() {
        Account phone = register("CUSTOMER", "Many Devices");
        Account laptop = login(phone.email(), "password123");

        assertThat(api.post("/api/auth/logout-all", Map.of()).status()).isEqualTo(401);
        assertThat(api.post("/api/auth/logout-all", Map.of(), laptop.token()).status()).isEqualTo(204);

        assertThat(refresh(phone.refreshToken()).status()).isEqualTo(401);
        assertThat(refresh(laptop.refreshToken()).status()).isEqualTo(401);
    }
}
