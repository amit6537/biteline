package com.fooddelivery.backend.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * RateLimiter — "at most N hits per time window" counters in Redis.
 *
 * CONCEPT: Fixed-window counter
 * ─────────────────────────────
 * key = "rl:login-fail:amit@x.com"
 *   1st hit → INCR makes it 1 and we set it to expire after the window
 *   next hits → INCR 2, 3, 4 …
 *   window over → Redis deletes the key → counting starts again at 0
 *
 * WHY Redis and not a HashMap in Java?
 *   With 3 app instances behind a load balancer, a HashMap would give an
 *   attacker 3× the attempts (one counter per instance). Redis is shared.
 *
 * WHY a Lua script?
 *   INCR and PEXPIRE as two separate calls could be split by a crash →
 *   a key that never expires → that user blocked forever. Redis runs a
 *   Lua script as ONE atomic step.
 *
 * Fail-open: if Redis is down we ALLOW the request (and log). Redis is our
 * cache, not a hard dependency; a cache outage must not block every login.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimiter {

    private static final String PREFIX = "rl:";

    /** Returns {count after this hit, milliseconds until the window resets}. */
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> HIT = RedisScript.of("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); ttl = tonumber(ARGV[1]) end
            return {n, ttl}
            """, List.class);

    private final StringRedisTemplate redis;

    /** allowed = still within the limit; retryAfterSeconds = when to try again if not. */
    public record Result(boolean allowed, long retryAfterSeconds) {
        static final Result ALLOW = new Result(true, 0);
    }

    /** Count one hit and say whether it is still within the limit. */
    @SuppressWarnings("unchecked")
    public Result hit(String key, int limit, Duration window) {
        try {
            List<Long> r = redis.execute(HIT, List.of(PREFIX + key), String.valueOf(window.toMillis()));
            long count = r.get(0);
            return count <= limit ? Result.ALLOW : new Result(false, toSeconds(r.get(1)));
        } catch (RuntimeException e) {
            log.warn("[RATE-LIMIT] Redis unavailable, allowing request: {}", e.getMessage());
            return Result.ALLOW;
        }
    }

    /** Check WITHOUT counting: is this key already at or over the limit? */
    public Result peek(String key, int limit) {
        try {
            String value = redis.opsForValue().get(PREFIX + key);
            if (value == null || Long.parseLong(value) < limit) {
                return Result.ALLOW;
            }
            Long ttlMs = redis.getExpire(PREFIX + key, java.util.concurrent.TimeUnit.MILLISECONDS);
            return new Result(false, toSeconds(ttlMs == null ? 1000 : ttlMs));
        } catch (RuntimeException e) {
            log.warn("[RATE-LIMIT] Redis unavailable, allowing request: {}", e.getMessage());
            return Result.ALLOW;
        }
    }

    /** Forget a counter (e.g. after a successful login). */
    public void reset(String key) {
        try {
            redis.delete(PREFIX + key);
        } catch (RuntimeException e) {
            log.warn("[RATE-LIMIT] Could not reset {}: {}", key, e.getMessage());
        }
    }

    private static long toSeconds(long millis) {
        return Math.max(1, (millis + 999) / 1000); // round up, never 0
    }
}
