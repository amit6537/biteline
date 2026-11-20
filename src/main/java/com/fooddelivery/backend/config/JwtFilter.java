package com.fooddelivery.backend.config;

import com.fooddelivery.backend.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtFilter — intercepts every HTTP request and validates the JWT.
 *
 * CONCEPT: Spring Security Filter Chain
 * ───────────────────────────────────────
 * Spring Security is a chain of filters. Every HTTP request passes
 * through ALL filters before reaching your @RestController.
 *
 * The chain (simplified):
 *   Request → [CorsFilter] → [JwtFilter (ours)] → [AuthorizationFilter] → Controller
 *
 * Our JwtFilter sits early in the chain. If the token is valid,
 * it sets the authenticated user in the SecurityContext.
 * The downstream AuthorizationFilter then checks if that user
 * has permission for the requested endpoint.
 *
 * CONCEPT: OncePerRequestFilter
 * ───────────────────────────────
 * Extends OncePerRequestFilter — guarantees this filter runs
 * exactly once per request, even with request dispatching (forwards/includes).
 * It's the standard base class for custom security filters.
 *
 * CONCEPT: SecurityContextHolder
 * ────────────────────────────────
 * This is where Spring Security keeps the current user's identity
 * for the duration of the request. Think of it as a thread-local
 * "who is logged in right now?" store.
 *
 * Setting it here means:
 *   - @PreAuthorize checks work automatically
 *   - @AuthenticationPrincipal works in controllers
 *   - SecurityContextHolder.getContext().getAuthentication() works anywhere
 */
@Component
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    // Explicit constructor: satisfies the IDE language server directly without Lombok processing delays
    public JwtFilter(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Extract the Authorization header
        final String authHeader = request.getHeader("Authorization");

        // 2. If no Bearer token, skip to next filter — public endpoints still work
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Extract the token (strip "Bearer " prefix)
        final String jwt = authHeader.substring(7);

        try {
            // 4. Extract email from token
            final String email = jwtUtil.extractEmail(jwt);

            // 5. Only authenticate if email found AND user not already authenticated
            //    (Avoid re-authenticating on the same request)
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 6. Load user from DB — also checks if user is active
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // 7. Validate token (signature + expiry + email match)
                if (jwtUtil.isTokenValid(jwt, userDetails.getUsername())) {

                    // 8. Build Spring Security's Authentication object
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,                        // credentials (not needed after auth)
                                    userDetails.getAuthorities() // roles → used by @PreAuthorize
                            );

                    // 9. Add request details (IP, session ID) — useful for audit logging
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    // 10. Place in SecurityContext — the user is now "authenticated"
                    //     for the rest of this request's lifecycle
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (ExpiredJwtException e) {
            log.debug("JWT token is expired: {}", e.getMessage());
            // Don't return 401 here — let the endpoint decide.
            // A public endpoint should still work with an expired token (just unauthed).
        } catch (JwtException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
        }

        // 11. Always continue the filter chain
        filterChain.doFilter(request, response);
    }
}

