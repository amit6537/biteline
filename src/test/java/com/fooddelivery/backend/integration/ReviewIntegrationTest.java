package com.fooddelivery.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.backend.support.AbstractIntegrationTest;
import com.fooddelivery.backend.support.Api;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 11: reviews, rating aggregates, edit window, moderation. */
class ReviewIntegrationTest extends AbstractIntegrationTest {

    private Town t;
    private Account rider;

    @BeforeEach
    void setUpTown() {
        t = newTown();
        rider = register("DELIVERY_PARTNER", "Review Rider");
        goOnline(rider, t.lat(), t.lng());
    }

    private String deliveredOrder() {
        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        deliver(t, rider, orderId);
        return orderId;
    }

    private Api.Response review(String orderId, Map<String, Object> body, Account who) {
        return api.post("/api/orders/" + orderId + "/review", body, who.token());
    }

    private JsonNode summary() {
        return api.get("/api/restaurants/" + t.restaurantId() + "/rating-summary").body();
    }

    @Test
    void validationAndOwnershipRules() {
        String orderId = deliveredOrder();
        Account stranger = register("CUSTOMER", "Not Mine");

        assertThat(review(orderId, Map.of("foodRating", 6), t.customer()).status()).isEqualTo(400);
        assertThat(review(orderId, Map.of("foodRating", 4, "comment", "x".repeat(1001)), t.customer()).status())
                .isEqualTo(400);
        assertThat(review(orderId, Map.of("foodRating", 4), stranger).status()).isEqualTo(403);
    }

    @Test
    void cannotReviewBeforeDeliveryOrAfterTheWindow() {
        String notDelivered = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        assertThat(review(notDelivered, Map.of("foodRating", 3), t.customer()).status()).isEqualTo(400);

        String old = deliveredOrder();
        jdbc.update("UPDATE orders SET delivered_at = NOW() - INTERVAL '8 days' WHERE id = ?::uuid", old);
        assertThat(review(old, Map.of("foodRating", 3), t.customer()).status()).isEqualTo(400);
    }

    @Test
    @DisplayName("Double-submit → exactly one review; comment trimmed; name shortened for privacy")
    void doubleSubmitAndPrivacy() {
        String orderId = deliveredOrder();
        Map<String, Object> body = Map.of("foodRating", 5, "deliveryRating", 4, "comment", "  Great!  ");

        var a = CompletableFuture.supplyAsync(() -> review(orderId, body, t.customer()));
        var b = CompletableFuture.supplyAsync(() -> review(orderId, body, t.customer()));
        List<Api.Response> results = List.of(a.join(), b.join());

        assertThat(results).extracting(Api.Response::status).containsExactlyInAnyOrder(201, 409);
        JsonNode created = results.stream().filter(r -> r.status() == 201).findFirst().orElseThrow().body();
        assertThat(created.path("comment").asText()).isEqualTo("Great!");
        assertThat(created.path("reviewerName").asText()).isEqualTo("Amit S.");

        // restaurant page shows the new rating immediately (cache evicted)
        JsonNode restaurant = api.get("/api/restaurants/" + t.restaurantId()).body();
        assertThat(restaurant.path("avgRating").asDouble()).isEqualTo(5.0);
        assertThat(restaurant.path("totalReviews").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("Running totals stay exact through create / edit / delete")
    void aggregateMaths() {
        Account admin = login("admin@test.local", "AdminPassword123!");
        review(deliveredOrder(), Map.of("foodRating", 5, "deliveryRating", 4), t.customer());
        review(deliveredOrder(), Map.of("foodRating", 4, "deliveryRating", 2), t.customer());
        String third = review(deliveredOrder(), Map.of("foodRating", 4), t.customer()).text("id");

        JsonNode s = summary();
        assertThat(s.path("avgRating").asDouble()).isEqualTo(4.33);   // (5+4+4)/3
        assertThat(s.path("totalReviews").asInt()).isEqualTo(3);
        assertThat(s.path("distribution").path("4").asInt()).isEqualTo(2);
        assertThat(s.path("distribution").path("1").asInt()).isZero();

        var edit = api.put("/api/reviews/" + third, Map.of("foodRating", 1, "comment", "cold"), t.customer().token());
        assertThat(edit.body().path("edited").asBoolean()).isTrue();
        assertThat(summary().path("avgRating").asDouble()).isEqualTo(3.33); // (5+4+1)/3

        var partner = api.get("/api/delivery/rating", rider.token()).body();
        assertThat(partner.path("avgDeliveryRating").asDouble()).isEqualTo(3.0); // delivery ratings (4+2)/2
        assertThat(partner.path("totalRatings").asInt()).isEqualTo(2);

        assertThat(api.delete("/api/reviews/" + third, t.customer().token()).status()).isEqualTo(403);
        assertThat(api.delete("/api/reviews/" + third, admin.token()).status()).isEqualTo(204);
        assertThat(summary().path("avgRating").asDouble()).isEqualTo(4.5); // (5+4)/2
        assertThat(summary().path("totalReviews").asInt()).isEqualTo(2);
        assertThat(api.get("/api/restaurants/" + t.restaurantId() + "/reviews").body()
                .path("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void editRules() {
        String reviewId = review(deliveredOrder(), Map.of("foodRating", 4), t.customer()).text("id");
        Account stranger = register("CUSTOMER", "Sneaky Editor");

        assertThat(api.put("/api/reviews/" + reviewId, Map.of("foodRating", 1), stranger.token()).status())
                .isEqualTo(403);

        jdbc.update("UPDATE reviews SET created_at = NOW() - INTERVAL '25 hours' WHERE id = ?::uuid", reviewId);
        assertThat(api.put("/api/reviews/" + reviewId, Map.of("foodRating", 1), t.customer().token()).status())
                .isEqualTo(400);
    }

    @Test
    void ownerIsNotifiedOfNewReviews() {
        String orderId = deliveredOrder();
        review(orderId, Map.of("foodRating", 5), t.customer());
        awaitNotification(t.owner(), "NEW_REVIEW", orderId);
    }
}
