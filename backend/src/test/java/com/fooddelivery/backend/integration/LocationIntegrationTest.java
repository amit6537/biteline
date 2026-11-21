package com.fooddelivery.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 12: nearby search, delivery radius, ETA, geofence, live tracking. */
class LocationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void addressCoordinatesAreValidated() {
        Account c = register("CUSTOMER", "Geo Customer");
        var onlyLat = api.post("/api/addresses", Map.of("street", "x", "city", "c", "state", "s",
                "pincode", "1", "latitude", 19.0), c.token());
        var badLat = api.post("/api/addresses", Map.of("street", "x", "city", "c", "state", "s",
                "pincode", "1", "latitude", 100, "longitude", 70), c.token());
        assertThat(onlyLat.status()).isEqualTo(400);
        assertThat(badLat.status()).isEqualTo(400);
    }

    @Test
    void orderOutsideDeliveryRadiusIsRejected() {
        Town t = newTown(); // delivers within 5 km
        String farAddress = addAddress(t.customer(), t.city(), north(t.lat(), 8), t.lng());
        api.post("/api/cart/items", Map.of("menuItemId", t.menuItemId(), "quantity", 1), t.customer().token());

        var r = api.post("/api/orders", Map.of("deliveryAddressId", farAddress), t.customer().token());
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.text("message")).contains("km away");
    }

    @Test
    @DisplayName("Nearby: nearest first, only open restaurants that deliver to me")
    void nearbySearch() {
        Town t = newTown(); // "Test Kitchen" at distance 0
        createRestaurant(t.owner(), "Second Spot", north(t.lat(), 1), t.lng(), t.city());
        createRestaurant(t.owner(), "Tiny Radius", north(t.lat(), 2), t.lng(), t.city(), Map.of("deliveryRadiusKm", 1));
        String closed = createRestaurant(t.owner(), "Closed Cafe", north(t.lat(), 0.5), t.lng(), t.city());
        api.patch("/api/restaurants/" + closed, Map.of("isOpen", false), t.owner().token());

        JsonNode results = api.get("/api/restaurants/nearby?lat=" + t.lat() + "&lng=" + t.lng() + "&radiusKm=5").body();
        assertThat(names(results)).containsExactly("Test Kitchen", "Second Spot");
        assertThat(results.get(1).path("distanceKm").asDouble()).isBetween(0.9, 1.1);
        assertThat(results.get(0).path("estimatedDeliveryMinutes").asLong()).isGreaterThanOrEqualTo(25);

        JsonNode all = api.get("/api/restaurants/nearby?lat=" + t.lat() + "&lng=" + t.lng()
                + "&radiusKm=5&openOnly=false").body();
        assertThat(names(all)).contains("Closed Cafe").doesNotContain("Tiny Radius");
    }

    @Test
    void nearbyRejectsBadInput() {
        assertThat(api.get("/api/restaurants/nearby?lat=19&lng=72&radiusKm=500").status()).isEqualTo(400);
        assertThat(api.get("/api/restaurants/nearby?lat=95&lng=10").status()).isEqualTo(400);
    }

    @Test
    @DisplayName("ETA changes with each step and shrinks as the rider approaches")
    void etaAndTracking() {
        Town t = newTown(); // customer 2 km north
        Account rider = register("DELIVERY_PARTNER", "Track Rider");
        Account stranger = register("CUSTOMER", "Peeping Tom");
        goOnline(rider, north(t.lat(), 1.5), t.lng());

        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        // PLACED: prep 20 + partner search 5 + 2 km ride (≈8) ≈ 33 min
        assertThat(tracking(t.customer(), orderId).path("etaMinutes").asLong()).isBetween(30L, 35L);
        assertThat(api.get("/api/orders/" + orderId + "/tracking", stranger.token()).status()).isEqualTo(403);

        advance(t.owner(), orderId, "accept", "preparing", "ready");
        awaitJob(rider, orderId);
        JsonNode ready = tracking(t.customer(), orderId);
        // rider 1.5 km → restaurant (≈6) + restaurant → customer (≈8)
        assertThat(ready.path("etaMinutes").asLong()).isBetween(10L, 16L);
        assertThat(ready.path("partnerName").asText()).isEqualTo("Track Rider");

        moveTo(rider, t.lat(), t.lng());
        api.patch("/api/orders/" + orderId + "/pickup", rider.token());
        long atRestaurant = tracking(t.customer(), orderId).path("etaMinutes").asLong();

        moveTo(rider, north(t.lat(), 1.5), t.lng()); // 0.5 km from the customer
        JsonNode close = tracking(t.customer(), orderId);
        assertThat(close.path("etaMinutes").asLong()).isLessThan(atRestaurant);
        assertThat(close.path("partnerDistanceKm").asDouble()).isLessThan(1.0);

        // Drop-off geofence: 0.5 km short is not "at the door"
        assertThat(api.patch("/api/orders/" + orderId + "/deliver", rider.token()).status()).isEqualTo(400);
        moveTo(rider, t.customerLat(), t.customerLng());
        assertThat(api.patch("/api/orders/" + orderId + "/deliver", rider.token()).status()).isEqualTo(200);

        JsonNode done = tracking(t.customer(), orderId);
        assertThat(done.path("partnerLatitude").isNull()).isTrue();  // rider's location private again
        assertThat(done.path("etaMinutes").isNull()).isTrue();
    }

    private JsonNode tracking(Account who, String orderId) {
        return api.get("/api/orders/" + orderId + "/tracking", who.token()).body();
    }

    private static List<String> names(JsonNode results) {
        List<String> names = new ArrayList<>();
        results.forEach(r -> names.add(r.path("restaurant").path("name").asText()));
        return names;
    }
}
