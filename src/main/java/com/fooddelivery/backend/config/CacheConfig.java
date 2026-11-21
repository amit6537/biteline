package com.fooddelivery.backend.config;

import com.fooddelivery.backend.dto.response.CachedPage;
import com.fooddelivery.backend.dto.response.MenuItemResponse;
import com.fooddelivery.backend.dto.response.RestaurantResponse;
import io.lettuce.core.ClientOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * CacheConfig — Phase 9: Redis as a cache in front of PostgreSQL.
 *
 * CONCEPT: Cache-Aside (a.k.a. "lazy loading")
 * ─────────────────────────────────────────────
 *   read:  look in Redis → HIT? return it.
 *                        → MISS? read Postgres, save copy in Redis (with TTL), return.
 *   write: update Postgres → DELETE the Redis copy ("evict").
 *          The next read will load the fresh value.
 * Postgres stays the source of truth; Redis is a disposable copy.
 *
 * CONCEPT: TTL (time to live)
 *   Every entry expires on its own after a while. Even if we ever forget an
 *   eviction, stale data can't live longer than the TTL.
 *
 * Edge cases handled here:
 *   1. Redis DOWN → app must still work (just slower). LoggingCacheErrorHandler
 *      turns cache errors into "cache miss" instead of a 500. Lettuce is told
 *      to REJECT commands while disconnected, so requests fail fast instead
 *      of hanging until a timeout.
 *   2. Evict-before-commit race → @EnableCaching(order = HIGHEST_PRECEDENCE)
 *      puts the cache layer OUTSIDE the @Transactional layer:
 *          request → [cache] → [transaction] → method
 *      so @CacheEvict runs only after the DB commit has finished. (If it ran
 *      before, another request could re-cache the OLD row in between.)
 *      Failed evictions (Redis down) are repaired by CacheHealer.
 *   3. DTO changes between deploys → keys are prefixed with a version
 *      ("fd:v1:..."). Bump app.cache.version and old entries are ignored.
 *      Unknown JSON fields are ignored too, so small changes need no bump.
 *   4. Silent evict failures → immediateWrites() (see below).
 *   5. "allEntries" eviction → uses SCAN in batches, not the KEYS command
 *      (KEYS blocks Redis while it walks every key).
 *   6. Typos in cache names → disableCreateOnMissingCache() fails loudly.
 *   7. Safe JSON → each cache has a serializer for ONE known type. We never
 *      store class names inside the JSON (that "default typing" style can be
 *      abused to make Jackson create arbitrary classes).
 */
@Configuration
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
public class CacheConfig implements CachingConfigurer {

    private final CacheHealer cacheHealer;

    public CacheConfig(CacheHealer cacheHealer) {
        this.cacheHealer = cacheHealer;
    }

    // ── Cache names (one place, no typos) ─────────────────────
    public static final String RESTAURANT = "restaurant";            // key: restaurantId
    public static final String MENU = "menu";                        // key: restaurantId
    public static final String RESTAURANT_LIST = "restaurantList";   // key: city:openOnly:page:size
    public static final String UNREAD_COUNT = "unreadCount";         // key: userId (Phase 10)

    @Value("${app.cache.version:v1}")
    private String cacheVersion;

    @Value("${app.cache.ttl.restaurant:PT10M}")
    private Duration restaurantTtl;

    @Value("${app.cache.ttl.menu:PT10M}")
    private Duration menuTtl;

    @Value("${app.cache.ttl.restaurant-list:PT1M}")
    private Duration restaurantListTtl;

    @Value("${app.cache.ttl.unread-count:PT5M}")
    private Duration unreadCountTtl;

    /** Delayed double delete: repeat every evict after this many ms (0 = off). */
    @Value("${app.cache.double-delete-delay-ms:1000}")
    private long doubleDeleteDelayMs;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        JsonMapper mapper = JsonMapper.builder()
                // a field removed from a DTO must not break reading old entries
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("fd:" + cacheVersion + ":") // fd:v1:restaurant::<id>
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()));

        JavaType menuType = mapper.getTypeFactory()
                .constructCollectionType(List.class, MenuItemResponse.class);
        JavaType restaurantPageType = mapper.getTypeFactory()
                .constructParametricType(CachedPage.class, RestaurantResponse.class);

        Map<String, RedisCacheConfiguration> caches = Map.of(
                RESTAURANT, typed(base, restaurantTtl,
                        new JacksonJsonRedisSerializer<>(mapper, RestaurantResponse.class)),
                MENU, typed(base, menuTtl,
                        new JacksonJsonRedisSerializer<>(mapper, menuType)),
                RESTAURANT_LIST, typed(base, restaurantListTtl,
                        new JacksonJsonRedisSerializer<>(mapper, restaurantPageType)),
                UNREAD_COUNT, typed(base, unreadCountTtl,
                        new JacksonJsonRedisSerializer<>(mapper, Long.class)));

        // immediateWrites(): Spring Data Redis 4 otherwise sends puts/EVICTS as
        // fire-and-forget async commands — a failed evict would vanish silently
        // and CacheHealer would never know the cache is stale.
        RedisCacheWriter writer = RedisCacheWriter.create(connectionFactory, cfg -> cfg
                .batchStrategy(BatchStrategies.scan(500))
                .immediateWrites());

        RedisCacheManager redis = RedisCacheManager.builder(writer)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(caches)
                .disableCreateOnMissingCache()
                .build();
        redis.afterPropertiesSet(); // not a bean itself → initialise its caches by hand

        // Wrap it: while a failed evict hasn't been repaired, reads skip Redis
        return new StaleGuardCacheManager(redis, cacheHealer, doubleDeleteDelayMs);
    }

    private static RedisCacheConfiguration typed(
            RedisCacheConfiguration base, Duration ttl, RedisSerializer<?> serializer) {
        return base.entryTtl(ttl).serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }

    /** Redis errors become a logged cache miss (never a 500); failed evicts get healed. */
    @Override
    public CacheErrorHandler errorHandler() {
        return cacheHealer;
    }

    /** CachingConfigurer wants this too — return the bean above. */
    @Override
    public CacheManager cacheManager() {
        return null; // null = "use the CacheManager bean from the context"
    }

    /**
     * While Redis is unreachable, reject commands immediately instead of
     * queueing them until the command timeout. Combined with the error
     * handler, a Redis outage costs ~0 ms per request instead of seconds.
     */
    @Bean
    public LettuceClientOptionsBuilderCustomizer failFastWhenRedisDown() {
        return builder -> builder.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }
}
