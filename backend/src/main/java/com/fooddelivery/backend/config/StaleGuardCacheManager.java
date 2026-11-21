package com.fooddelivery.backend.config;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * StaleGuardCacheManager — wraps the real Redis cache manager.
 *
 * While CacheHealer says "cache may be stale" (an evict failed during a
 * Redis outage and the cache hasn't been cleared yet), every READ is
 * treated as a miss and goes straight to Postgres. Evicts/clears still go
 * through. Once the healer clears Redis, normal caching resumes.
 *
 * It also does DELAYED DOUBLE DELETE for every evict/clear:
 *
 *   THE RACE (without it):
 *     t=0ms   Reader A: cache miss → reads OLD row from Postgres (slow request)
 *     t=5ms   Writer B: updates Postgres, commits, evicts the cache key
 *     t=9ms   Reader A: puts the OLD row into the cache  ← stale for a whole TTL!
 *
 *   THE FIX: evict now, and evict AGAIN ~1 second later. Reader A's late
 *   put is wiped by the second delete. The leftover window is only "a read
 *   slower than the delay", which is extremely rare, and TTL still bounds it.
 *
 * CONCEPT: Decorator pattern
 *   Same interface (Cache), wraps another Cache, adds one behaviour, and
 *   forwards everything else. Callers (@Cacheable etc.) can't tell the difference.
 */
@Slf4j
public class StaleGuardCacheManager implements CacheManager, DisposableBean {

    private final CacheManager delegate;
    private final CacheHealer healer;
    private final long doubleDeleteDelayMs;

    // One small background thread just for the delayed second deletes
    private final ScheduledExecutorService delayedEvictor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cache-double-delete");
        t.setDaemon(true);
        return t;
    });

    public StaleGuardCacheManager(CacheManager delegate, CacheHealer healer, long doubleDeleteDelayMs) {
        this.delegate = delegate;
        this.healer = healer;
        this.doubleDeleteDelayMs = doubleDeleteDelayMs;
    }

    /** Run the same removal again after the delay; failures go to the healer. */
    private void later(Runnable removal) {
        if (doubleDeleteDelayMs <= 0) return;
        delayedEvictor.schedule(() -> {
            try {
                removal.run();
            } catch (RuntimeException e) {
                log.warn("[CACHE] Delayed second delete failed: {}", e.getMessage());
                healer.markPossiblyStale();
            }
        }, doubleDeleteDelayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        delayedEvictor.shutdownNow();
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = delegate.getCache(name);
        return cache == null ? null : new GuardedCache(cache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }

    private final class GuardedCache implements Cache {
        private final Cache target;

        GuardedCache(Cache target) {
            this.target = target;
        }

        private boolean untrusted() {
            return healer.isPossiblyStale();
        }

        // ── reads: skipped while stale ────────────────────────
        @Override
        public ValueWrapper get(Object key) {
            return untrusted() ? null : target.get(key);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            return untrusted() ? null : target.get(key, type);
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            if (untrusted()) {
                try {
                    return valueLoader.call(); // load from DB, don't cache
                } catch (Exception e) {
                    throw new ValueRetrievalException(key, valueLoader, e);
                }
            }
            return target.get(key, valueLoader);
        }

        @Override
        public CompletableFuture<?> retrieve(Object key) {
            return untrusted() ? null : target.retrieve(key);
        }

        @Override
        public <T> CompletableFuture<T> retrieve(Object key, Supplier<CompletableFuture<T>> valueLoader) {
            return untrusted() ? valueLoader.get() : target.retrieve(key, valueLoader);
        }

        // ── writes: skipped while stale (healer will wipe anyway) ──
        @Override
        public void put(Object key, Object value) {
            if (!untrusted()) target.put(key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            return untrusted() ? null : target.putIfAbsent(key, value);
        }

        // ── removals: always forwarded, then repeated after a delay ──
        @Override
        public void evict(Object key) {
            target.evict(key);
            later(() -> target.evict(key));
        }

        @Override
        public boolean evictIfPresent(Object key) {
            boolean removed = target.evictIfPresent(key);
            later(() -> target.evict(key));
            return removed;
        }

        @Override
        public void clear() {
            target.clear();
            later(target::clear);
        }

        @Override
        public boolean invalidate() {
            boolean result = target.invalidate();
            later(target::clear);
            return result;
        }

        @Override
        public String getName() {
            return target.getName();
        }

        @Override
        public Object getNativeCache() {
            return target.getNativeCache();
        }
    }
}
