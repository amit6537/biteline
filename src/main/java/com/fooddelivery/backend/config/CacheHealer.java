package com.fooddelivery.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CacheHealer — handles Redis errors AND repairs the cache after an outage.
 *
 * THE PROBLEM:
 *   Redis is down. An owner renames their restaurant → Postgres is updated,
 *   but the "delete cached copy" (evict) fails. Redis comes back with its
 *   saved data (appendonly=yes) → it still holds the OLD name → customers
 *   see stale data until the TTL runs out.
 *
 * THE FIX:
 *   1. Any failed evict/clear marks the cache as "possibly stale".
 *   2. Every few seconds, if marked, try to CLEAR all our caches.
 *      Redis still down → stays marked, try again later.
 *      Redis back       → everything cleared, flag reset. Next reads reload
 *                         fresh data from Postgres.
 *   Clearing everything is blunt but always correct: the only cost is a
 *   few cache misses.
 *
 * All errors are also logged and swallowed (LoggingCacheErrorHandler):
 * a cache problem must never turn into a 500 for the user.
 */
@Component
@Slf4j
public class CacheHealer extends LoggingCacheErrorHandler {

    private final ObjectProvider<CacheManager> cacheManager; // lazy: avoids a circular dependency
    private final AtomicBoolean possiblyStale = new AtomicBoolean(false);

    public CacheHealer(ObjectProvider<CacheManager> cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        super.handleCacheEvictError(exception, cache, key);
        markPossiblyStale();
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        super.handleCacheClearError(exception, cache);
        markPossiblyStale();
    }

    /** StaleGuardCacheManager asks this before every read. */
    public boolean isPossiblyStale() {
        return possiblyStale.get();
    }

    /** Also called by code that evicts manually (outside @CacheEvict). */
    public void markPossiblyStale() {
        if (possiblyStale.compareAndSet(false, true)) {
            log.warn("[CACHE] An eviction failed — cache will be cleared once Redis is reachable.");
        }
    }

    @Scheduled(fixedDelayString = "${app.cache.heal-interval-ms:5000}")
    public void healIfNeeded() {
        if (!possiblyStale.get()) {
            return;
        }
        try {
            CacheManager manager = cacheManager.getObject();
            for (String name : manager.getCacheNames()) {
                Cache cache = manager.getCache(name);
                if (cache != null) cache.clear();
            }
            possiblyStale.set(false);
            log.info("[CACHE] Redis reachable again — all caches cleared, no stale data left.");
        } catch (RuntimeException e) {
            log.debug("[CACHE] Redis still unreachable, will retry: {}", e.getMessage());
        }
    }
}
