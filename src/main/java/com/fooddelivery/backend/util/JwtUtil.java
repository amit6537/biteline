package com.fooddelivery.backend.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JwtUtil — creates and validates JSON Web Tokens.
 *
 * CONCEPT: What IS a JWT?
 * ────────────────────────
 * A JWT has 3 parts separated by dots:
 *   Header.Payload.Signature
 *
 *   Header  → algorithm used (HS256) and type (JWT) — Base64 encoded
 *   Payload → "claims": userId, email, role, expiry — Base64 encoded
 *   Signature → HMAC_SHA256(header + "." + payload, secret)
 *
 * Example (decoded):
 *   Header:  { "alg": "HS256", "typ": "JWT" }
 *   Payload: { "sub": "uuid-here", "role": "CUSTOMER",
 *               "iat": 1700000000, "exp": 1700086400 }
 *   Signature: (computed using our secret key)
 *
 * WHY is it secure?
 *   Anyone can decode the header & payload (they're Base64, not encrypted).
 *   But they CANNOT forge a valid signature without our secret.
 *   So we can trust the claims IF the signature verifies.
 *
 * CONCEPT: Stateless Authentication
 * ────────────────────────────────────
 * Traditional sessions: server stores session in memory/DB.
 *   Problem: doesn't scale horizontally (server 1 doesn't know
 *   about session created on server 2).
 *
 * JWT: server stores NOTHING. The token IS the session.
 *   Any server with the same secret can verify any token.
 *   This scales to 1000 servers with zero shared state.
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    // ── Token generation ──────────────────────────────────────

    /**
     * Generate a short-lived access token.
     * We embed the user's role as a custom "claim" so we can
     * do role checks without hitting the DB.
     */
    public String generateAccessToken(String email, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("type", "access");
        return buildToken(claims, email, accessTokenExpiryMs);
    }

    /**
     * Generate a long-lived refresh token.
     * Minimal claims — just enough to identify the user.
     */
    public String generateRefreshToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return buildToken(claims, email, refreshTokenExpiryMs);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiry) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)                              // "sub" = email
                .issuedAt(new Date())                          // "iat" = now
                .expiration(new Date(System.currentTimeMillis() + expiry)) // "exp"
                .signWith(getSigningKey())                     // signs with HMAC-SHA256
                .compact();                                    // builds the final string
    }

    // ── Token validation ──────────────────────────────────────

    /**
     * Validate token: check signature + not expired.
     * jjwt throws an exception if either check fails —
     * we catch it in JwtFilter and return 401.
     */
    public boolean isTokenValid(String token, String expectedEmail) {
        final String email = extractEmail(token);
        return email.equals(expectedEmail) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ── Claims extraction ─────────────────────────────────────

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    public long getAccessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }

    /**
     * Generic claim extractor for custom claims.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claims != null ? claimsResolver.apply(claims) : null;
    }

    private Claims extractAllClaims(String token) {
        // parseSignedClaims() both decodes AND verifies the signature.
        // If signature is invalid or token is expired → exception is thrown.
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = hexStringToBytes(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] hexStringToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}

