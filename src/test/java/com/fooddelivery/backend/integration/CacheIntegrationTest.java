package com.fooddelivery.backend.integration;

import com.fooddelivery.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Phase 9: Redis cache-aside, eviction, double delete, and surviving a
 * Redis outage. We peek into the real Redis container with StringRedisTemplate.
 *
 * Keys look like  fd:v2:restaurant::<id>  (prefix:version:cacheName::key)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // outage test runs last
class CacheIntegrationTest extends AbstractIntegrationTest {

    private static String restaurantKey(String id) {
        return "fd:v2:restaurant::" + id;
    }

    @Test
    @Order(1)
    void restaurantIsCachedWithTtlAsReadableJson() {
        Town t = newTown();
        api.get("/api/restaurants/" + t.restaurantId());

        String key = restaurantKey(t.restaurantId());
        assertThat(redis.hasKey(key)).isTrue();
        assertThat(redis.getExpire(key)).isBetween(1L, 600L);
        assertThat(redis.opsForValue().get(key)).contains("\"name\":\"Test Kitchen\"");
    }

    @Test
    @Order(2)
    void updateEvictsSoReadersSeeFreshData() {
        Town t = newTown();
        api.get("/api/restaurants/" + t.restaurantId());

        api.patch("/api/restaurants/" + t.restaurantId(), Map.of("name", "Renamed"), t.owner().token());

        assertThat(redis.hasKey(restaurantKey(t.restaurantId()))).isFalse();
        assertThat(api.get("/api/restaurants/" + t.restaurantId()).text("name")).isEqualTo("Renamed");
    }

    @Test
    @Order(3)
    void menuChangeRefreshesTheCachedMenu() {
        Town t = newTown();
        api.get("/api/restaurants/" + t.restaurantId() + "/menu");
        assertThat(redis.hasKey("fd:v2:menu::" + t.restaurantId())).isTrue();

        addMenuItem(t.owner(), t.restaurantId(), "Vada", 40);

        assertThat(api.get("/api/restaurants/" + t.restaurantId() + "/menu").body().size()).isEqualTo(2);
    }

    @Test
    @Order(4)
    void cityListIsCachedButDeepPagesAreNot() {
        Town t = newTown();
        String city = t.city().toLowerCase();
        api.get("/api/restaurants?city=" + t.city());
        api.get("/api/restaurants?city=" + t.city() + "&page=7");

        assertThat(redis.hasKey("fd:v2:restaurantList::" + city + ":true:0:20")).isTrue();
        assertThat(redis.hasKey("fd:v2:restaurantList::" + city + ":true:7:20")).isFalse();

        createRestaurant(t.owner(), "Brand New", t.lat(), t.lng(), t.city());
        assertThat(api.get("/api/restaurants?city=" + t.city()).body().path("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    @Order(5)
    @DisplayName("Delayed double delete wipes a stale copy re-cached right after the evict")
    void delayedDoubleDelete() {
        Town t = newTown();
        String key = restaurantKey(t.restaurantId());
        api.get("/api/restaurants/" + t.restaurantId());
        String oldJson = redis.opsForValue().get(key);

        api.patch("/api/restaurants/" + t.restaurantId(), Map.of("name", "New Name"), t.owner().token());
        redis.opsForValue().set(key, oldJson); // a slow reader puts the OLD row back

        await().atMost(Duration.ofSeconds(3)).until(() -> !redis.hasKey(key));
        assertThat(api.get("/api/restaurants/" + t.restaurantId()).text("name")).isEqualTo("New Name");
    }

    @Test
    @Order(99)
    @DisplayName("Redis outage: app keeps answering from Postgres, and no stale data afterwards")
    void survivesRedisOutage() {
        Town t = newTown();
        api.get("/api/restaurants/" + t.restaurantId()); // cached

        var docker = REDIS.getDockerClient();
        docker.pauseContainerCmd(REDIS.getContainerId()).exec(); // Redis frozen
        try {
            long start = System.currentTimeMillis();
            assertThat(api.get("/api/restaurants/" + t.restaurantId()).status()).isEqualTo(200);
            assertThat(api.get("/api/restaurants/" + t.restaurantId() + "/menu").status()).isEqualTo(200);
            var update = api.patch("/api/restaurants/" + t.restaurantId(),
                    Map.of("description", "edited during outage"), t.owner().token());
            assertThat(update.status()).isEqualTo(200);
            assertThat(System.currentTimeMillis() - start).isLessThan(10_000); // fails fast, no 60 s hangs
        } finally {
            docker.unpauseContainerCmd(REDIS.getContainerId()).exec();
        }

        // The evict during the outage failed → healer must wipe the stale copy
        await().atMost(Duration.ofSeconds(15)).until(() ->
                "edited during outage".equals(api.get("/api/restaurants/" + t.restaurantId()).text("description")));
    }
}
