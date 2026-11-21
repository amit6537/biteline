package com.fooddelivery.backend.integration;

import com.fooddelivery.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 2 + the security fixes from the review. */
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Nobody can self-register as ADMIN")
    void adminSelfRegistrationIsBlocked() {
        var r = api.post("/api/auth/register", Map.of("name", "Hacker", "email", "h@t.com",
                "phone", "9876543210", "password", "password123", "role", "ADMIN"));
        assertThat(r.status()).isEqualTo(400);
    }

    @Test
    void loginWithWrongPasswordIs401() {
        Account a = register("CUSTOMER", "Login Test");
        assertThat(api.post("/api/auth/login", Map.of("email", a.email(), "password", "wrong-pass")).status())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("A refresh token cannot be used as an access token")
    void refreshTokenIsNotALoginToken() {
        Account a = register("CUSTOMER", "Refresh Test");
        assertThat(api.get("/api/cart", a.refreshToken()).status()).isEqualTo(401);
        assertThat(api.get("/api/cart", a.token()).status()).isEqualTo(200);
    }

    @Test
    void noTokenOnProtectedWriteIs401() {
        assertThat(api.post("/api/restaurants", Map.of("name", "x")).status()).isEqualTo(401);
        assertThat(api.get("/api/orders/my").status()).isEqualTo(401);
    }

    @Test
    void wrongRoleIs403() {
        Account customer = register("CUSTOMER", "Role Test");
        var r = api.post("/api/restaurants", Map.of("name", "X", "latitude", 19.0, "longitude", 72.8, "city", "Y"),
                customer.token());
        assertThat(r.status()).isEqualTo(403);
    }

    @Test
    void browsingRestaurantsIsPublic() {
        assertThat(api.get("/api/restaurants?city=Nowhere").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("A deactivated user's token stops working immediately")
    void deactivatedUserIsLockedOut() {
        Account a = register("CUSTOMER", "Banned User");
        jdbc.update("UPDATE users SET is_active = false WHERE email = ?", a.email());
        assertThat(api.get("/api/cart", a.token()).status()).isEqualTo(401);
    }

    @Test
    void badInputIs400NotA500() {
        Account a = register("CUSTOMER", "Input Test");
        assertThat(api.get("/api/orders/not-a-uuid", a.token()).status()).isEqualTo(400);
        assertThat(api.post("/api/cart/items", "{bad json", a.token()).status()).isEqualTo(400);
        assertThat(api.get("/api/restaurants").status()).isEqualTo(400); // missing ?city=
    }

    @Test
    void unknownEndpointIs404() {
        Account a = register("CUSTOMER", "Route Test");
        assertThat(api.get("/api/does-not-exist", a.token()).status()).isEqualTo(404);
    }

    @Test
    void errorResponsesHaveAConsistentJsonShape() {
        var r = api.post("/api/auth/register", Map.of("email", "not-an-email"));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.body().has("timestamp")).isTrue();
        assertThat(r.body().path("fieldErrors").has("email")).isTrue();
    }
}
