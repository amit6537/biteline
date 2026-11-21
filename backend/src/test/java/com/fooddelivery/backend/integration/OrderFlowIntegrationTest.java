package com.fooddelivery.backend.integration;

import com.fooddelivery.backend.support.AbstractIntegrationTest;
import com.fooddelivery.backend.support.Api;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** Phases 3–6: restaurants, cart, orders, state machine, payments. */
class OrderFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Boolean fields keep their 'is' prefix in JSON and are saved from requests")
    void jsonFieldNames() {
        Town t = newTown();
        var restaurant = api.get("/api/restaurants/" + t.restaurantId());
        assertThat(restaurant.body().has("isOpen")).isTrue();
        assertThat(restaurant.body().has("open")).isFalse(); // no duplicate key

        var item = api.post("/api/restaurants/" + t.restaurantId() + "/menu",
                Map.of("name", "Paneer", "price", 250, "category", "Starters", "isVegetarian", true), t.owner().token());
        assertThat(item.body().path("isVegetarian").asBoolean()).isTrue();

        var address = api.post("/api/addresses", Map.of("street", "S", "city", t.city(), "state", "S",
                "pincode", "1", "isDefault", true), t.customer().token());
        assertThat(address.body().path("isDefault").asBoolean()).isTrue();
    }

    @Test
    void pageSizeIsCapped() {
        assertThat(api.get("/api/restaurants?city=X&size=100000").body().path("size").asInt()).isEqualTo(50);
    }

    @Test
    void cartQuantityIsValidated() {
        Town t = newTown();
        String noQty = "{\"menuItemId\":\"" + t.menuItemId() + "\",\"quantity\":null}";
        assertThat(api.post("/api/cart/items", noQty, t.customer().token()).status()).isEqualTo(400);
        assertThat(api.post("/api/cart/items", Map.of("menuItemId", t.menuItemId(), "quantity", 51),
                t.customer().token()).status()).isEqualTo(400);
    }

    @Test
    @DisplayName("A cart holds items from one restaurant only")
    void singleRestaurantCart() {
        Town t = newTown();
        String otherRestaurant = createRestaurant(t.owner(), "Other", t.lat(), t.lng(), t.city());
        String otherItem = addMenuItem(t.owner(), otherRestaurant, "Dosa", 80);

        api.post("/api/cart/items", Map.of("menuItemId", t.menuItemId(), "quantity", 1), t.customer().token());
        var r = api.post("/api/cart/items", Map.of("menuItemId", otherItem, "quantity", 1), t.customer().token());
        assertThat(r.status()).isEqualTo(409);
    }

    @Test
    void orderSnapshotsPricesAndEmptiesTheCart() {
        Town t = newTown();
        api.post("/api/cart/items", Map.of("menuItemId", t.menuItemId(), "quantity", 2), t.customer().token());
        var order = api.post("/api/orders", Map.of("deliveryAddressId", t.addressId()), t.customer().token());

        assertThat(order.status()).isEqualTo(201);
        assertThat(order.body().path("subtotal").asDouble()).isEqualTo(400.0);   // 2 × 200
        assertThat(order.body().path("totalAmount").asDouble()).isEqualTo(430.0); // + 30 delivery

        // owner raises the price later → the order keeps the old price
        api.patch("/api/restaurants/" + t.restaurantId() + "/menu/" + t.menuItemId(), Map.of("price", 999), t.owner().token());
        var again = api.get("/api/orders/" + order.text("id"), t.customer().token());
        assertThat(again.body().path("items").get(0).path("price").asDouble()).isEqualTo(200.0);

        assertThat(api.get("/api/cart", t.customer().token()).body().path("totalItemCount").asInt()).isZero();
    }

    @Test
    @DisplayName("Double-clicking 'Place order' creates exactly one order")
    void doubleSubmitCreatesOneOrder() {
        Town t = newTown();
        api.post("/api/cart/items", Map.of("menuItemId", t.menuItemId(), "quantity", 1), t.customer().token());

        var first = CompletableFuture.supplyAsync(() ->
                api.post("/api/orders", Map.of("deliveryAddressId", t.addressId()), t.customer().token()));
        var second = CompletableFuture.supplyAsync(() ->
                api.post("/api/orders", Map.of("deliveryAddressId", t.addressId()), t.customer().token()));
        List<Integer> codes = List.of(first.join().status(), second.join().status());

        assertThat(codes).containsExactlyInAnyOrder(201, 400);
    }

    @Test
    void stateMachineRejectsSkippingSteps() {
        Town t = newTown();
        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        assertThat(api.patch("/api/orders/" + orderId + "/ready", t.owner().token()).status()).isEqualTo(400);
        assertThat(api.patch("/api/orders/" + orderId + "/accept", t.owner().token()).status()).isEqualTo(200);
    }

    @Test
    void onlyTheOwnerOfThisRestaurantCanAccept() {
        Town t = newTown();
        Account otherOwner = register("RESTAURANT_OWNER", "Other Owner");
        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        assertThat(api.patch("/api/orders/" + orderId + "/accept", otherOwner.token()).status()).isEqualTo(403);
    }

    @Test
    @DisplayName("Payment idempotency: same key = same payment; someone else's key = rejected")
    void paymentIdempotency() {
        Town t = newTown();
        Account stranger = register("CUSTOMER", "Stranger Danger");
        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        String key = "idem-" + UUID.randomUUID();
        Map<String, Object> body = Map.of("orderId", orderId, "method", "UPI", "idempotencyKey", key);

        Api.Response first = api.post("/api/payments/process", body, t.customer().token());
        Api.Response replay = api.post("/api/payments/process", body, t.customer().token());
        Api.Response stolen = api.post("/api/payments/process", body, stranger.token());
        Api.Response newKey = api.post("/api/payments/process",
                Map.of("orderId", orderId, "method", "UPI", "idempotencyKey", "other-" + UUID.randomUUID()),
                t.customer().token());

        assertThat(first.status()).isEqualTo(200);
        assertThat(first.text("status")).isEqualTo("SUCCESS");
        assertThat(replay.text("id")).isEqualTo(first.text("id"));
        assertThat(stolen.status()).isIn(403, 409);
        assertThat(newKey.status()).isEqualTo(400); // already paid
    }

    @Test
    void cancellingAPaidOrderRefundsIt() {
        Town t = newTown();
        String orderId = placeOrder(t.customer(), t.menuItemId(), t.addressId());
        api.post("/api/payments/process", Map.of("orderId", orderId, "method", "CARD"), t.customer().token());

        var cancel = api.patch("/api/orders/" + orderId + "/cancel", Map.of("reason", "changed mind"), t.customer().token());
        assertThat(cancel.status()).isEqualTo(200);
        assertThat(api.get("/api/payments/order/" + orderId, t.customer().token()).text("status")).isEqualTo("REFUNDED");
    }

    @Test
    void deletingAnAddressUsedByOrdersIs409() {
        Town t = newTown();
        placeOrder(t.customer(), t.menuItemId(), t.addressId());
        assertThat(api.delete("/api/addresses/" + t.addressId(), t.customer().token()).status()).isEqualTo(409);
    }
}
