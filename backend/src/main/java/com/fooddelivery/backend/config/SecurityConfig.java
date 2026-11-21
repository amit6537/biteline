package com.fooddelivery.backend.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * SecurityConfig — the master configuration for Spring Security.
 *
 * CONCEPT: Spring Security Architecture
 * ────────────────────────────────────────
 * SecurityFilterChain defines WHICH endpoints need authentication
 * and HOW requests are authenticated.
 *
 * Key decisions explained below.
 *
 * CONCEPT: Stateless Session
 * ────────────────────────────
 * SessionCreationPolicy.STATELESS tells Spring Security:
 * "Never create an HTTP session. Every request must be self-contained."
 * This is correct for REST APIs using JWT — there's no session cookie.
 *
 * CONCEPT: CSRF (Cross-Site Request Forgery)
 * ────────────────────────────────────────────
 * CSRF protection is for browser-based session auth (cookies).
 * Since we use JWT in the Authorization header (not cookies),
 * CSRF attacks don't apply — we disable it to avoid complications.
 *
 * CONCEPT: BCrypt Password Hashing
 * ──────────────────────────────────
 * BCrypt is an adaptive hashing function with a "cost factor."
 * Cost factor 12 means 2^12 = 4096 iterations of hashing.
 * This makes brute-force attacks computationally expensive.
 * As hardware gets faster, you raise the cost factor.
 *
 * CONCEPT: DaoAuthenticationProvider
 * ─────────────────────────────────────
 * This is Spring Security's DB-backed authentication provider.
 * We configure it with:
 * - Our UserDetailsService (loads user from DB)
 * - BCryptPasswordEncoder (knows how to compare hashed passwords)
 * Spring Security uses it during login to validate credentials.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize on controller methods
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final UserDetailsServiceImpl userDetailsService;

    // Explicit constructor: satisfies IDE language server directly
    public SecurityConfig(JwtFilter jwtFilter, UserDetailsServiceImpl userDetailsService) {
        this.jwtFilter = jwtFilter;
        this.userDetailsService = userDetailsService;
    }

    /**
     * The filter chain — defines security rules for each request.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Disable CSRF — not needed for stateless JWT APIs
                // (the token travels in a header, which other sites can't make a browser send)
                .csrf(csrf -> csrf.disable())

                // CORS — let OUR frontend (another origin, e.g. localhost:5173)
                // call this API from the browser. Rules: corsConfigurationSource() below.
                .cors(Customizer.withDefaults())

                // Authorization rules — evaluated top to bottom, first match wins
                .authorizeHttpRequests(auth -> auth
                        // Logging out every device needs to know WHO you are
                        // (must come before the /api/auth/** permitAll below)
                        .requestMatchers("/api/auth/logout-all").authenticated()
                        // Public endpoints — no token required
                        .requestMatchers(
                                "/api/auth/**", // register, login, refresh, logout
                                "/actuator/health", "/actuator/info"
                        ).permitAll()
                        // Browsing restaurants & menus is public — but ONLY reads.
                        // POST/PATCH/DELETE on restaurants still need a token.
                        .requestMatchers(HttpMethod.GET, "/api/restaurants/**").permitAll()
                        // Everything else requires a valid JWT
                        .anyRequest().authenticated())

                // STATELESS — no HTTP sessions, every request must carry a JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // No token / bad token → 401, valid token but wrong role → 403.
                // (Spring's default would answer 403 for both.)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeError(res, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((req, res, e) ->
                                writeError(res, HttpStatus.FORBIDDEN, "Access denied")))

                // Wire our AuthenticationProvider (DB + BCrypt)
                .authenticationProvider(authenticationProvider())

                // Insert our JwtFilter BEFORE Spring's default
                // UsernamePasswordAuthenticationFilter.
                // WHY? The default filter handles form-login (username/password in body).
                // Our JwtFilter handles Bearer tokens in the header.
                // By running first, we set the SecurityContext before any auth check happens.
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    /**
     * CONCEPT: CORS (Cross-Origin Resource Sharing)
     * ─────────────────────────────────────────────
     * A browser only lets JavaScript from https://a.com read responses from
     * https://b.com if b.com says so in "Access-Control-Allow-*" headers.
     * Before a POST with JSON or an Authorization header, the browser first
     * sends an OPTIONS "preflight" request to ask. Spring answers it here.
     *
     * Only origins listed in app.cors.allowed-origins get those headers.
     * Postman / curl / other servers don't care about CORS — it protects
     * the user's BROWSER from other websites, it is not authentication.
     *
     * allowCredentials=false: we send the JWT in the Authorization header,
     * not in cookies, so the browser doesn't need to attach cookies.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L); // browser may cache the preflight answer for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Writes the same JSON error shape GlobalExceptionHandler uses. */
    static void writeError(HttpServletResponse res, HttpStatus status, String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}",
                LocalDateTime.now(), status.value(), status.getReasonPhrase(), message));
    }

    /**
     * BCryptPasswordEncoder — used to hash passwords on register
     * and verify on login.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // cost factor 12
    }

    /**
     * DaoAuthenticationProvider — connects Spring Security to our DB.
     * AuthService calls authenticationManager.authenticate() during login,
     * which delegates to this provider.
     *
     * CONCEPT: Spring Security 7 Breaking Change
     * ────────────────────────────────────────────
     * In Spring Security 7 (bundled with Spring Boot 4.x):
     * - The no-arg constructor DaoAuthenticationProvider() was removed.
     * - setUserDetailsService() was removed.
     * - UserDetailsService is now REQUIRED in the constructor.
     *
     * This enforces that you can't accidentally create a provider
     * without a UserDetailsService — a fail-fast design improvement.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager — the entry point for triggering authentication.
     * AuthService uses this to validate credentials during login.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
