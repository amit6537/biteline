package com.fooddelivery.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Admin bootstrap, stuck-order alerts, manual assignment, ShedLock. */
class AdminIntegrationTest extends AbstractIntegrationTest {

    private Account admin() {
        return login("admin@test.local", "AdminPassword123!");
    }

    @Test
    @DisplayName("First admin is created from config at startup")
    void adminBootstrap() {
        assertThat(api.post("/api/auth/login", Map.of("email", "admin@test.local",
                "password", "AdminPassword123!")).text("role")).isEqualTo("ADMIN");
    }

    @Test
    void adminEndpointsAreAdminOnly() {
        Account customer = register("CUSTOMER", "Wannabe Admin");
        assertThat(api.get("/api/admin/orders/unassigned", customer.token()).status()).isEqualTo(403);
        assertThat(api.get("/api/admin/orders/unassigned", admin().token()).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("Order stuck without a partner → customer told + admins alerted ONCE → admin assigns manually")
    void stuckOrderFlow() throws InterruptedException {
        Account admin = admin();
        Town t = newTown();
        Account rider = register("DELIVERY_PARTNER", "Manual Rider");
        goOffline(rider, t.lat(), t.lng()); // has the app, but offline → automatic assignment won't pick them

        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        advance(t.owner(), orderId, "accept", "preparing", "ready");
        jdbc.update("UPDATE orders SET ready_at = NOW() - INTERVAL '20 minutes' WHERE id = ?::uuid", orderId);

        awaitNotification(t.customer(), "DELIVERY_DELAYED", orderId);
        awaitNotification(admin, "OPS_ALERT", orderId);

        Thread.sleep(2500); // a few more scheduler rounds
        int admins = jdbc.queryForObject("SELECT count(*) FROM users WHERE role = 'ADMIN' AND is_active", Integer.class);
        int alerts = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE type = 'OPS_ALERT' "
                + "AND order_id = ?::uuid", Integer.class, orderId);
        assertThat(alerts).isEqualTo(admins); // one per admin, not one per scheduler round

        JsonNode unassigned = api.get("/api/admin/orders/unassigned", admin.token()).body();
        boolean listed = false;
        for (JsonNode o : unassigned) {
            if (o.path("orderId").asText().equals(orderId)) {
                listed = o.path("alerted").asBoolean() && o.path("waitingMinutes").asLong() >= 19;
            }
        }
        assertThat(listed).isTrue();

        // Manual assignment rules
        assertThat(api.post("/api/admin/orders/" + orderId + "/assign",
                Map.of("partnerId", t.customer().userId()), admin.token()).status()).isEqualTo(400); // not a partner
        var assigned = api.post("/api/admin/orders/" + orderId + "/assign",
                Map.of("partnerId", rider.userId()), admin.token());
        assertThat(assigned.status()).isEqualTo(200);
        assertThat(assigned.text("partnerName")).isEqualTo("Manual Rider");
        assertThat(api.post("/api/admin/orders/" + orderId + "/assign",
                Map.of("partnerId", rider.userId()), admin.token()).status()).isEqualTo(409); // already assigned
        assertThat(currentJob(rider).path("orderId").asText()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("ShedLock keeps a lock row for the delivery scheduler")
    void shedLockRowExists() {
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM shedlock WHERE name = 'deliveryAssignmentRetry'", Integer.class) == 1);
    }
}
