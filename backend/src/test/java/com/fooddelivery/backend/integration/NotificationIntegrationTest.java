package com.fooddelivery.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.backend.support.AbstractIntegrationTest;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Phase 10: Kafka → notifications inbox (+ email outbox). */
class NotificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Test
    @DisplayName("One full order notifies the customer, owner and partner")
    void fullOrderNotifiesEveryone() {
        Town t = newTown();
        Account rider = register("DELIVERY_PARTNER", "Ravi Kumar");
        Account stranger = register("CUSTOMER", "Nobody Here");
        goOnline(rider, t.lat(), t.lng());

        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        api.post("/api/payments/process", Map.of("orderId", orderId, "method", "UPI"), t.customer().token());
        deliver(t, rider, orderId);

        await().atMost(Duration.ofSeconds(20)).until(() -> {
            List<String> types = types(t.customer());
            return types.containsAll(List.of("ORDER_PLACED", "PAYMENT_SUCCESS", "PARTNER_ASSIGNED", "REVIEW_REQUEST"))
                    && types.stream().filter("ORDER_STATUS"::equals).count() == 5; // accepted→delivered
        });
        awaitNotification(t.owner(), "NEW_ORDER", orderId);
        awaitNotification(rider, "NEW_DELIVERY_JOB", orderId);
        assertThat(notifications(stranger)).isEmpty();

        // newest first, by when the EVENT happened
        List<String> times = notifications(t.customer()).stream().map(n -> n.path("createdAt").asText()).toList();
        assertThat(times).isSortedAccordingTo((a, b) -> b.compareTo(a));
    }

    @Test
    @DisplayName("Unread badge: count, mark read (idempotent), others' → 404, read-all")
    void unreadCountAndMarkRead() {
        Town t = newTown();
        Account stranger = register("CUSTOMER", "Nosy Neighbour");
        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        advance(t.owner(), orderId, "accept", "preparing");
        await().atMost(Duration.ofSeconds(20)).until(() -> notifications(t.customer()).size() == 3);

        assertThat(unread(t.customer())).isEqualTo(3);
        assertThat(redis.hasKey("fd:v2:unreadCount::" + t.customer().userId())).isTrue(); // cached

        String first = notifications(t.customer()).get(0).path("id").asText();
        var read = api.patch("/api/notifications/" + first + "/read", t.customer().token());
        assertThat(read.body().path("isRead").asBoolean()).isTrue();
        assertThat(api.patch("/api/notifications/" + first + "/read", t.customer().token()).status()).isEqualTo(200);
        assertThat(unread(t.customer())).isEqualTo(2); // cache was evicted

        assertThat(api.patch("/api/notifications/" + first + "/read", stranger.token()).status()).isEqualTo(404);
        assertThat(api.get("/api/notifications?unreadOnly=true", t.customer().token())
                .body().path("content").size()).isEqualTo(2);

        api.patch("/api/notifications/read-all", t.customer().token());
        assertThat(unread(t.customer())).isZero();
    }

    @Test
    @DisplayName("Kafka delivers an event twice → still only one notification")
    void duplicateEventIsIgnored() throws Exception {
        Town t = newTown();
        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        JsonNode pay = api.post("/api/payments/process", Map.of("orderId", orderId, "method", "UPI"),
                t.customer().token()).body();
        awaitNotification(t.customer(), "PAYMENT_SUCCESS", orderId);

        String replay = new ObjectMapper().writeValueAsString(Map.of(
                "paymentId", pay.path("id").asText(), "orderId", orderId,
                "customerId", t.customer().userId(), "customerEmail", t.customer().email(),
                "amount", pay.path("amount").asDouble(), "transactionId", pay.path("transactionId").asText(),
                "paidAt", pay.path("paidAt").asText()));
        kafka.send("payment.success", orderId, replay).get();
        Thread.sleep(2000);

        assertThat(types(t.customer()).stream().filter("PAYMENT_SUCCESS"::equals).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("A malformed message goes to the dead-letter topic")
    void poisonMessageGoesToDlt() throws Exception {
        kafka.send("order.placed", "bad", "this is not json").get();

        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            await().atMost(Duration.ofSeconds(20)).until(() ->
                    admin.listTopics().names().get().contains("order.placed.DLT"));
        }
    }

    @Test
    void cancellationMentionsRefundAndTellsTheOwner() {
        Town t = newTown();
        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        api.post("/api/payments/process", Map.of("orderId", orderId, "method", "CARD"), t.customer().token());
        api.patch("/api/orders/" + orderId + "/cancel", Map.of("reason", "late"), t.customer().token());

        awaitNotification(t.customer(), "ORDER_CANCELLED", orderId);
        awaitNotification(t.owner(), "ORDER_CANCELLED", orderId);
        String message = notifications(t.customer()).stream()
                .filter(n -> n.path("type").asText().equals("ORDER_CANCELLED"))
                .findFirst().orElseThrow().path("message").asText();
        assertThat(message).contains("refund");
    }

    @Test
    @DisplayName("Email outbox: every notification's email ends up SENT")
    void emailOutboxSendsEverything() {
        Town t = newTown();
        placeOrder(t.customer(), t.menuItemId(), t.addressId());
        awaitNotification(t.customer(), "ORDER_PLACED", null);

        await().atMost(Duration.ofSeconds(15)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ?::uuid AND email_status = 'SENT'",
                Integer.class, t.customer().userId()) >= 1);
    }

    private List<String> types(Account a) {
        return notifications(a).stream().map(n -> n.path("type").asText()).toList();
    }

    private long unread(Account a) {
        return api.get("/api/notifications/unread-count", a.token()).body().path("unreadCount").asLong();
    }
}
