package com.fooddelivery.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Phase 8: delivery assignment (+ the Phase 8 review fixes, cooldown, geofence). */
class DeliveryIntegrationTest extends AbstractIntegrationTest {

    @Test
    void partnerEndpointsArePartnerOnly() {
        Account customer = register("CUSTOMER", "Not Partner");
        Account partner = register("DELIVERY_PARTNER", "New Partner");
        assertThat(api.get("/api/delivery/status", customer.token()).status()).isEqualTo(403);
        assertThat(api.get("/api/delivery/status", partner.token()).status()).isEqualTo(404); // never online yet
    }

    @Test
    @DisplayName("Nearest FREE partner within range gets the order")
    void nearestAvailablePartnerIsAssigned() {
        Town t = newTown();
        Account near = register("DELIVERY_PARTNER", "Near Rider");         // 1.5 km
        Account mid = register("DELIVERY_PARTNER", "Mid Rider");           // 4.5 km
        Account far = register("DELIVERY_PARTNER", "Far Rider");           // 20 km, outside 10 km radius
        Account offline = register("DELIVERY_PARTNER", "Offline Rider");   // closest, but offline
        goOnline(near, north(t.lat(), 1.5), t.lng());
        goOnline(mid, north(t.lat(), 4.5), t.lng());
        goOnline(far, north(t.lat(), 20), t.lng());
        goOffline(offline, north(t.lat(), 0.1), t.lng());

        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        advance(t.owner(), orderId, "accept", "preparing", "ready");

        JsonNode job = awaitJob(near, orderId);
        assertThat(job.path("status").asText()).isEqualTo("ASSIGNED");
        assertThat(currentJob(offline)).isNull();
        assertThat(currentJob(mid)).isNull();

        JsonNode status = api.get("/api/delivery/status", near.token()).body();
        assertThat(status.path("isAvailable").asBoolean()).isFalse();       // busy now
        assertThat(status.path("currentAssignmentId").asText()).isEqualTo(job.path("id").asText());
    }

    @Test
    @DisplayName("Only the assigned partner can pick up / view; reject → next nearest; rejecter loses access")
    void assignedPartnerRulesAndReject() {
        Town t = newTown();
        Account near = register("DELIVERY_PARTNER", "Near Rider");
        Account mid = register("DELIVERY_PARTNER", "Mid Rider");
        goOnline(near, north(t.lat(), 1), t.lng());
        goOnline(mid, north(t.lat(), 3), t.lng());

        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        advance(t.owner(), orderId, "accept", "preparing", "ready");
        JsonNode job = awaitJob(near, orderId);

        assertThat(api.patch("/api/orders/" + orderId + "/pickup", mid.token()).status()).isEqualTo(403);
        assertThat(api.get("/api/orders/" + orderId, mid.token()).status()).isEqualTo(403);
        assertThat(api.get("/api/orders/" + orderId, near.token()).status()).isEqualTo(200);

        var reject = api.post("/api/delivery/assignments/" + job.path("id").asText() + "/reject", null, near.token());
        assertThat(reject.status()).isEqualTo(200);
        assertThat(reject.text("partnerName")).isEqualTo("Mid Rider");
        assertThat(currentJob(near)).isNull();
        assertThat(api.get("/api/orders/" + orderId, near.token()).status()).isEqualTo(403); // address hidden now
    }

    @Test
    @DisplayName("Geofence: pickup only at the restaurant")
    void pickupGeofence() {
        Town t = newTown();
        Account rider = register("DELIVERY_PARTNER", "Geo Rider");
        goOnline(rider, north(t.lat(), 2), t.lng());
        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        advance(t.owner(), orderId, "accept", "preparing", "ready");
        awaitJob(rider, orderId);

        var tooFar = api.patch("/api/orders/" + orderId + "/pickup", rider.token());
        assertThat(tooFar.status()).isEqualTo(400);
        assertThat(tooFar.text("message")).contains("km from the restaurant");

        moveTo(rider, t.lat(), t.lng());
        assertThat(api.patch("/api/orders/" + orderId + "/pickup", rider.token()).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("Going offline mid-delivery is respected after the delivery")
    void offlineMidDeliveryStaysOffline() {
        Town t = newTown();
        Account rider = register("DELIVERY_PARTNER", "Tired Rider");
        goOnline(rider, t.lat(), t.lng());
        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        advance(t.owner(), orderId, "accept", "preparing", "ready");
        awaitJob(rider, orderId);

        // busy partner says "online" → still not available while busy
        var busy = goOnline(rider, t.lat(), t.lng());
        assertThat(busy.body().path("isOnline").asBoolean()).isTrue();
        assertThat(busy.body().path("isAvailable").asBoolean()).isFalse();

        api.patch("/api/orders/" + orderId + "/pickup", rider.token());
        goOffline(rider, t.customerLat(), t.customerLng()); // "last one for today"
        assertThat(api.patch("/api/orders/" + orderId + "/deliver", rider.token()).status()).isEqualTo(200);

        JsonNode status = api.get("/api/delivery/status", rider.token()).body();
        assertThat(status.path("isOnline").asBoolean()).isFalse();
        assertThat(status.path("isAvailable").asBoolean()).isFalse();
        assertThat(api.get("/api/delivery/assignments", rider.token()).body()
                .path("content").get(0).path("status").asText()).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("No partner free → order waits → partner comes online → scheduler assigns it")
    void schedulerAssignsWaitingOrders() throws InterruptedException {
        Town t = newTown();
        Account rider = register("DELIVERY_PARTNER", "Late Rider");
        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        advance(t.owner(), orderId, "accept", "preparing", "ready");
        Thread.sleep(1500);

        goOnline(rider, t.lat(), t.lng());
        awaitJob(rider, orderId);
    }

    @Test
    @DisplayName("One free partner, two orders READY at once → never two jobs")
    void oneFreePartnerNeverGetsTwoOrders() {
        Town t = newTown();
        Account rider = register("DELIVERY_PARTNER", "Solo Rider");
        goOnline(rider, t.lat(), t.lng());
        String o1 = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        String o2 = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        advance(t.owner(), o1, "accept", "preparing");
        advance(t.owner(), o2, "accept", "preparing");

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> api.patch("/api/orders/" + o1 + "/ready", t.owner().token())),
                CompletableFuture.runAsync(() -> api.patch("/api/orders/" + o2 + "/ready", t.owner().token()))).join();

        await().atMost(Duration.ofSeconds(10)).until(() -> currentJob(rider) != null);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4)).until(() ->
                activeAssignments(o1, o2) == 1);
    }

    @Test
    @DisplayName("Partner's phone dies after assignment → job released and reassigned")
    void silentPartnerIsReleased() {
        Town t = newTown();
        Account ghost = register("DELIVERY_PARTNER", "Ghost Rider");
        Account backup = register("DELIVERY_PARTNER", "Backup Rider");
        goOnline(ghost, t.lat(), t.lng());
        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        advance(t.owner(), orderId, "accept", "preparing", "ready");
        awaitJob(ghost, orderId);

        jdbc.update("UPDATE delivery_partner_status SET last_seen_at = NOW() - INTERVAL '20 minutes' "
                + "WHERE partner_id = ?::uuid", ghost.userId());
        goOnline(backup, north(t.lat(), 1), t.lng());

        awaitJob(backup, orderId);
        assertThat(jdbc.queryForObject("SELECT status FROM delivery_assignments WHERE order_id = ?::uuid "
                + "AND partner_id = ?::uuid", String.class, orderId, ghost.userId())).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Reject cooldown: not re-offered right away, but re-offered later (never stuck forever)")
    void rejectCooldown() throws InterruptedException {
        Town t = newTown();
        Account rider = register("DELIVERY_PARTNER", "Picky Rider");
        goOnline(rider, t.lat(), t.lng());
        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        advance(t.owner(), orderId, "accept", "preparing", "ready");
        JsonNode job = awaitJob(rider, orderId);

        api.post("/api/delivery/assignments/" + job.path("id").asText() + "/reject", null, rider.token());
        Thread.sleep(2500); // two scheduler rounds
        assertThat(currentJob(rider)).isNull();

        jdbc.update("UPDATE delivery_assignments SET cancelled_at = NOW() - INTERVAL '10 minutes' "
                + "WHERE order_id = ?::uuid", orderId);
        awaitJob(rider, orderId);
    }

    private int activeAssignments(String o1, String o2) {
        return jdbc.queryForObject("SELECT count(*) FROM delivery_assignments WHERE order_id IN (?::uuid, ?::uuid) "
                + "AND status IN ('ASSIGNED','PICKED_UP')", Integer.class, o1, o2);
    }
}
