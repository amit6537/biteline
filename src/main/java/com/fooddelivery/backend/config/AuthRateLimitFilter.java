package com.fooddelivery.backend.config;

import com.fooddelivery.backend.util.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * AuthRateLimitFilter — caps how often ONE IP may call login / register / refresh.
 *
 * This is the coarse outer wall (default 30 per minute per IP). It stops a
 * script from spraying thousands of requests: creating fake accounts,
 * trying a password on many accounts, or just making BCrypt (slow on
 * purpose) eat all our CPU. The per-account wrong-password lock lives in
 * LoginAttemptService.
 *
 * It's a plain servlet filter, so it answers 429 BEFORE the request
 * reaches the controller or touches the database.
 */
@Component
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS =
            Set.of("/api/auth/login", "/api/auth/register", "/api/auth/refresh");

    private final RateLimiter rateLimiter;
    private final RateLimitProperties props;

    public AuthRateLimitFilter(RateLimiter rateLimiter, RateLimitProperties props) {
        this.rateLimiter = rateLimiter;
        this.props = props;
    }

    /** Only POSTs to the three auth endpoints (OPTIONS preflights are never counted). */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !props.isEnabled()
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        RateLimiter.Result r = rateLimiter.hit("auth-ip:" + ip, props.getAuthPerIpLimit(), props.getAuthPerIpWindow());
        if (!r.allowed()) {
            log.warn("[RATE-LIMIT] {} blocked on {} for {}s", ip, request.getRequestURI(), r.retryAfterSeconds());
            response.setHeader("Retry-After", String.valueOf(r.retryAfterSeconds()));
            SecurityConfig.writeError(response, HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests. Try again in " + r.retryAfterSeconds() + " seconds.");
            return;
        }
        chain.doFilter(request, response);
    }
}
