package com.fooddelivery.backend.service;

import com.fooddelivery.backend.config.RateLimitProperties;
import com.fooddelivery.backend.exception.TooManyRequestsException;
import com.fooddelivery.backend.util.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * LoginAttemptService — stops password guessing.
 *
 * Two counters of WRONG passwords:
 *   1. account + IP  (5 per 15 min)  → one attacker hammering one account
 *   2. account alone (20 per hour)   → many machines attacking one account
 *
 * Once a counter is full, even the CORRECT password gets 429 until the
 * window ends. That's the point: otherwise the attacker just keeps guessing
 * until one guess returns 200.
 *
 * A successful login clears counter 1 (the person at that IP knows the
 * password). Counter 2 is NOT cleared: failures from other IPs still count.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties props;

    /** Call BEFORE checking the password. Throws 429 if the account is locked for this caller. */
    public void checkAllowed(String email, String ip) {
        if (!props.isEnabled()) {
            return;
        }
        RateLimiter.Result byAccountIp = rateLimiter.peek(accountIpKey(email, ip), props.getLoginFailPerAccountIpLimit());
        RateLimiter.Result byAccount = rateLimiter.peek(accountKey(email), props.getLoginFailPerAccountLimit());
        if (!byAccountIp.allowed() || !byAccount.allowed()) {
            long wait = Math.max(byAccountIp.retryAfterSeconds(), byAccount.retryAfterSeconds());
            throw new TooManyRequestsException(
                    "Too many failed login attempts. Try again in " + wait + " seconds.", wait);
        }
    }

    /** Wrong password → count it on both counters. */
    public void recordFailure(String email, String ip) {
        if (!props.isEnabled()) {
            return;
        }
        rateLimiter.hit(accountIpKey(email, ip), Integer.MAX_VALUE, props.getLoginFailPerAccountIpWindow());
        RateLimiter.Result r = rateLimiter.hit(accountKey(email), props.getLoginFailPerAccountLimit(),
                props.getLoginFailPerAccountWindow());
        if (!r.allowed()) {
            log.warn("[AUTH] Account {} locked: too many failed logins (last from {})", email, ip);
        }
    }

    /** Right password → this IP's failures are forgiven. */
    public void recordSuccess(String email, String ip) {
        if (props.isEnabled()) {
            rateLimiter.reset(accountIpKey(email, ip));
        }
    }

    // "Amit@X.com" and "amit@x.com" must share one counter
    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String accountIpKey(String email, String ip) {
        return "login-fail:" + normalize(email) + ":" + ip;
    }

    private static String accountKey(String email) {
        return "login-fail:" + normalize(email);
    }
}
