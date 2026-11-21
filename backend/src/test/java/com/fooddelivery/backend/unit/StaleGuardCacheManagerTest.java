package com.fooddelivery.backend.unit;

import com.fooddelivery.backend.config.CacheHealer;
import com.fooddelivery.backend.config.StaleGuardCacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The cache wrapper's two tricks, tested with an in-memory cache
 * (ConcurrentMapCacheManager) instead of Redis — the logic is the same.
 */
class StaleGuardCacheManagerTest {

    private ConcurrentMapCacheManager inMemory;
    private CacheHealer healer;
    private StaleGuardCacheManager manager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        inMemory = new ConcurrentMapCacheManager("restaurant");
        ObjectProvider<CacheManager> provider = mock(ObjectProvider.class);
        healer = new CacheHealer(provider);
        manager = new StaleGuardCacheManager(inMemory, healer, 200); // second delete after 200 ms
        when(provider.getObject()).thenReturn(manager);
    }

    @AfterEach
    void tearDown() {
        manager.destroy();
    }

    @Test
    void delayedDoubleDeleteWipesALateStalePut() {
        Cache cache = manager.getCache("restaurant");
        cache.put("r1", "old name");

        cache.evict("r1");                                          // writer evicts
        inMemory.getCache("restaurant").put("r1", "old name");      // slow reader re-caches OLD data
        assertThat(cache.get("r1")).isNotNull();                    // stale for now...

        await().atMost(Duration.ofSeconds(2))
                .until(() -> inMemory.getCache("restaurant").get("r1") == null); // ...gone after 200 ms
    }

    @Test
    void whilePossiblyStaleReadsBypassTheCache() {
        Cache cache = manager.getCache("restaurant");
        cache.put("r1", "cached");

        healer.markPossiblyStale();   // an evict failed during an outage

        assertThat(cache.get("r1")).isNull();                               // treated as a miss
        assertThat(cache.get("r1", () -> "from database")).isEqualTo("from database");
        cache.put("r1", "new");                                             // writes skipped too
        assertThat(inMemory.getCache("restaurant").get("r1").get()).isEqualTo("cached");
    }

    @Test
    void healerClearsEverythingAndTrustsTheCacheAgain() {
        Cache cache = manager.getCache("restaurant");
        cache.put("r1", "stale");
        healer.markPossiblyStale();

        healer.healIfNeeded();

        assertThat(healer.isPossiblyStale()).isFalse();
        assertThat(cache.get("r1")).isNull();      // stale entry wiped
        cache.put("r1", "fresh");
        assertThat(cache.get("r1").get()).isEqualTo("fresh");
    }
}
