package com.fooddelivery.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Rate-limit settings, bound from app.rate-limit.* in application.properties.
 *
 * CONCEPT: @ConfigurationProperties
 *   Instead of many @Value fields, Spring fills this whole object from
 *   every property that starts with the prefix:
 *   app.rate-limit.auth-per-ip-limit=30  →  authPerIpLimit = 30
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
@Getter
@Setter
public class RateLimitProperties {

    /** Master switch. */
    private boolean enabled = true;

    /** Any POST to login / register / refresh, per client IP. */
    private int authPerIpLimit = 30;
    private Duration authPerIpWindow = Duration.ofMinutes(1);

    /** Wrong passwords for ONE account from ONE IP (normal brute force). */
    private int loginFailPerAccountIpLimit = 5;
    private Duration loginFailPerAccountIpWindow = Duration.ofMinutes(15);

    /** Wrong passwords for ONE account from ALL IPs (attack from many machines). */
    private int loginFailPerAccountLimit = 20;
    private Duration loginFailPerAccountWindow = Duration.ofHours(1);
}
