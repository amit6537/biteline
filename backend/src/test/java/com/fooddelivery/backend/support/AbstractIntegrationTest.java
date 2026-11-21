package com.fooddelivery.backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;

/**
 * AbstractIntegrationTest — the base every integration test extends.
 *
 * CONCEPT: Testcontainers (singleton pattern)
 * ───────────────────────────────────────────
 * The `static` block starts REAL Postgres, Redis and Kafka in Docker ONCE
 * for the whole test run (starting them per class would take minutes).
 * @DynamicPropertySource then points the app at those containers' random
 * ports. When the JVM exits, Testcontainers deletes the containers.
 *
 * CONCEPT: @SpringBootTest(RANDOM_PORT)
 *   Starts the whole app on a free port, like `java -jar`. Tests call it
 *   over HTTP through {@link Api}. Every test class shares ONE app instance
 *   (Spring caches the context), so the suite stays fast.
 *
 * Tests never clean the DB. Instead each test uses fresh random users and a
 * random map location / city, so tests can't see each other's data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    protected static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer("confluentinc/cp-kafka:7.6.0");

    static {
        Startables.deepStart(POSTGRES, REDIS, KAFKA).join(); // all three in parallel
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Value("${local.server.port}")
    protected int port;

    @Autowired protected JdbcTemplate jdbc;            // peek/poke the DB directly
    @Autowired protected StringRedisTemplate redis;    // peek into Redis

    protected Api api;

    @BeforeEach
    void createApiClient() {
        api = new Api("http://localhost:" + port);
    }

    // ═════════════════════════════════════════════════════════
    // Helpers — build test data in one line
    // ═════════════════════════════════════════════════════════

    protected record Account(String token, String refreshToken, String userId, String email, String name) {}

    protected Account register(String role, String name) {
        String email = name.split(" ")[0].toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8) + "@t.com";
        String phone = "9" + String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
        Api.Response r = api.post("/api/auth/register", Map.of(
                "name", name, "email", email, "phone", phone, "password", "password123", "role", role));
        if (r.status() != 201) throw new IllegalStateException("register failed: " + r);
        return new Account(r.text("accessToken"), r.text("refreshToken"), r.text("userId"), email, name);
    }

    protected Account login(String email, String password) {
        Api.Response r = api.post("/api/auth/login", Map.of("email", email, "password", password));
        if (r.status() != 200) throw new IllegalStateException("login failed: " + r);
        return new Account(r.text("accessToken"), r.text("refreshToken"), r.text("userId"), email, r.text("name"));
    }

    /** A random spot far from other tests' data (each test gets its own "town"). */
    protected static double[] randomSpot() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        return new double[]{rnd.nextDouble(10, 30), rnd.nextDouble(70, 85)};
    }

    /** Move `km` kilometres north (1° latitude ≈ 111 km). */
    protected static double north(double lat, double km) {
        return lat + km / 111.0;
    }

    protected static String randomCity() {
        return "City" + UUID.randomUUID().toString().substring(0, 6);
    }

    protected String createRestaurant(Account owner, String name, double lat, double lng, String city) {
        return createRestaurant(owner, name, lat, lng, city, Map.of());
    }

    protected String createRestaurant(Account owner, String name, double lat, double lng, String city,
                                      Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>(Map.of("name", name, "latitude", lat, "longitude", lng, "city", city));
        body.putAll(extra);
        Api.Response r = api.post("/api/restaurants", body, owner.token());
        if (r.status() != 201) throw new IllegalStateException("restaurant failed: " + r);
        return r.text("id");
    }

    protected String addMenuItem(Account owner, String restaurantId, String name, int price) {
        Api.Response r = api.post("/api/restaurants/" + restaurantId + "/menu",
                Map.of("name", name, "price", price, "category", "Main"), owner.token());
        if (r.status() != 201) throw new IllegalStateException("menu item failed: " + r);
        return r.text("id");
    }

    /** lat/lng may be null → an address without coordinates. */
    protected String addAddress(Account customer, String city, Double lat, Double lng) {
        Map<String, Object> body = new HashMap<>(Map.of("street", "1 Test Road", "city", city,
                "state", "TS", "pincode", "123456"));
        if (lat != null) body.put("latitude", lat);
        if (lng != null) body.put("longitude", lng);
        Api.Response r = api.post("/api/addresses", body, customer.token());
        if (r.status() != 201) throw new IllegalStateException("address failed: " + r);
        return r.text("id");
    }

    /** Adds one item to the cart and places the order. Returns the order id. */
    protected String placeOrder(Account customer, String menuItemId, String addressId) {
        api.post("/api/cart/items", Map.of("menuItemId", menuItemId, "quantity", 1), customer.token());
        Api.Response r = api.post("/api/orders", Map.of("deliveryAddressId", addressId), customer.token());
        if (r.status() != 201) throw new IllegalStateException("order failed: " + r);
        return r.text("id");
    }

    /** Owner moves the order through steps, e.g. advance(owner, id, "accept", "preparing", "ready"). */
    protected void advance(Account owner, String orderId, String... steps) {
        for (String step : steps) {
            Api.Response r = api.patch("/api/orders/" + orderId + "/" + step, owner.token());
            if (r.status() != 200) throw new IllegalStateException(step + " failed: " + r);
        }
    }

    protected Api.Response goOnline(Account partner, double lat, double lng) {
        return api.put("/api/delivery/status", Map.of("available", true, "latitude", lat, "longitude", lng), partner.token());
    }

    protected Api.Response goOffline(Account partner, double lat, double lng) {
        return api.put("/api/delivery/status", Map.of("available", false, "latitude", lat, "longitude", lng), partner.token());
    }

    /** GPS ping only — doesn't change online/offline. */
    protected Api.Response moveTo(Account partner, double lat, double lng) {
        return api.put("/api/delivery/status", Map.of("latitude", lat, "longitude", lng), partner.token());
    }

    /** The partner's current job, or null (204). */
    protected JsonNode currentJob(Account partner) {
        Api.Response r = api.get("/api/delivery/assignments/current", partner.token());
        return r.status() == 200 ? r.body() : null;
    }

    /** Wait (assignment is async via Kafka) until the partner holds this order. */
    protected JsonNode awaitJob(Account partner, String orderId) {
        AtomicReference<JsonNode> job = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(300)).until(() -> {
            JsonNode j = currentJob(partner);
            job.set(j);
            return j != null && orderId.equals(j.path("orderId").asText());
        });
        return job.get();
    }

    /**
     * A fresh, isolated "town" for one test: an owner with a restaurant at a
     * random spot (delivers within 5 km, 20 min prep), one menu item, and a
     * customer whose address is 2 km north of the restaurant.
     */
    protected record Town(double lat, double lng, String city, Account owner, Account customer,
                          String restaurantId, String menuItemId, String addressId,
                          double customerLat, double customerLng) {}

    protected Town newTown() {
        double[] spot = randomSpot();
        String city = randomCity();
        Account owner = register("RESTAURANT_OWNER", "Owner Test");
        Account customer = register("CUSTOMER", "Amit Sharma");
        String restaurantId = createRestaurant(owner, "Test Kitchen", spot[0], spot[1], city,
                Map.of("deliveryRadiusKm", 5, "avgPrepMinutes", 20));
        String menuItemId = addMenuItem(owner, restaurantId, "Thali", 200);
        double custLat = north(spot[0], 2);
        String addressId = addAddress(customer, city, custLat, spot[1]);
        return new Town(spot[0], spot[1], city, owner, customer, restaurantId, menuItemId, addressId, custLat, spot[1]);
    }

    /**
     * Drive an order from PLACED to DELIVERED with `partner` (who must already
     * be online nearby): owner prepares it, partner gets assigned, rides to
     * the restaurant, picks up, rides to the customer, delivers.
     */
    protected void deliver(Town t, Account partner, String orderId) {
        advance(t.owner(), orderId, "accept", "preparing", "ready");
        awaitJob(partner, orderId);
        moveTo(partner, t.lat(), t.lng());
        expectStatus(api.patch("/api/orders/" + orderId + "/pickup", partner.token()), 200);
        moveTo(partner, t.customerLat(), t.customerLng());
        expectStatus(api.patch("/api/orders/" + orderId + "/deliver", partner.token()), 200);
    }

    protected static void expectStatus(Api.Response r, int status) {
        if (r.status() != status) {
            throw new AssertionError("expected HTTP " + status + " but got " + r.status() + ": " + r.body());
        }
    }

    protected List<JsonNode> notifications(Account account) {
        Api.Response r = api.get("/api/notifications?size=50", account.token());
        List<JsonNode> list = new ArrayList<>();
        r.body().path("content").forEach(list::add);
        return list;
    }

    protected boolean hasNotification(Account account, String type, String orderId) {
        return notifications(account).stream().anyMatch(n ->
                type.equals(n.path("type").asText()) && (orderId == null || orderId.equals(n.path("orderId").asText())));
    }

    protected void awaitNotification(Account account, String type, String orderId) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(300))
                .until(() -> hasNotification(account, type, orderId));
    }
}
