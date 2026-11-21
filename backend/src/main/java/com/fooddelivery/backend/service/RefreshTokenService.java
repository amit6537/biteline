package com.fooddelivery.backend.service;

import com.fooddelivery.backend.entity.RefreshToken;
import com.fooddelivery.backend.entity.User;
import com.fooddelivery.backend.exception.UnauthorizedException;
import com.fooddelivery.backend.repository.RefreshTokenRepository;
import com.fooddelivery.backend.repository.UserRepository;
import com.fooddelivery.backend.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RefreshTokenService — issues, rotates and revokes refresh tokens.
 *
 * CONCEPT: Refresh token rotation
 * ───────────────────────────────
 * Access token  = short-lived (15 min), sent on every API call.
 * Refresh token = long-lived (7 days), sent ONLY to /api/auth/refresh.
 *
 * Every /refresh call:
 *   1. checks the old refresh token (signature, type, DB row still active)
 *   2. revokes it (reason ROTATED)
 *   3. returns a NEW access token + a NEW refresh token (same "family")
 * So each refresh token works exactly once.
 *
 * CONCEPT: Reuse detection
 * ────────────────────────
 * If a token that was already rotated shows up again, two parties hold it:
 * the real user and someone who copied it. We can't tell which is which,
 * so we revoke the whole family → both are logged out, the thief included.
 *
 * Exception: the same token arriving twice within a few seconds is almost
 * always the user's own app (two tabs refreshing at the same moment), so
 * inside that grace window we just answer 401 without logging anyone out.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    static final String ROTATED = "ROTATED";
    static final String LOGOUT = "LOGOUT";
    static final String LOGOUT_ALL = "LOGOUT_ALL";
    static final String REUSE = "REUSE";

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.auth.refresh-reuse-grace-seconds:10}")
    private long reuseGraceSeconds;

    /** Result of a successful rotation: who it is + their new refresh token. */
    public record Rotation(User user, String refreshToken) {}

    /** Login / register → a brand-new family (a new "device session"). */
    @Transactional
    public String issueForNewLogin(User user) {
        return issue(user, UUID.randomUUID());
    }

    /**
     * Swap an old refresh token for a new one.
     *
     * noRollbackFor: on reuse we revoke the family AND throw 401. Without
     * this, the exception would roll back the revoke and the thief would
     * keep a working token.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String token) {
        Claims claims = parseRefreshToken(token);
        UUID tokenId = UUID.fromString(claims.getId());
        RefreshToken row = refreshTokenRepository.findById(tokenId).orElseThrow(this::invalid);

        User user = userRepository.findById(row.getUserId()).orElseThrow(this::invalid);
        if (!user.getEmail().equals(claims.getSubject())) {
            throw invalid();
        }
        if (!user.isActive()) {
            throw new UnauthorizedException("Account is disabled");
        }

        LocalDateTime now = LocalDateTime.now();
        if (refreshTokenRepository.revokeIfActive(tokenId, now, ROTATED) == 1) {
            return new Rotation(user, issue(user, row.getFamilyId()));
        }

        // Already revoked. Re-read it: the copy loaded above may be from
        // before a parallel request revoked it.
        RefreshToken current = refreshTokenRepository.findById(tokenId).orElseThrow(this::invalid);
        if (!ROTATED.equals(current.getRevokeReason())) {
            throw new UnauthorizedException("Session has ended, please log in again");
        }
        if (current.getRevokedAt().isAfter(now.minusSeconds(reuseGraceSeconds))) {
            throw new UnauthorizedException("Refresh token was already used, retry with the newest one");
        }

        int revoked = refreshTokenRepository.revokeFamily(row.getFamilyId(), now, REUSE);
        log.warn("[AUTH] Refresh token reuse for user {} — revoked {} token(s) of that session",
                user.getEmail(), revoked);
        throw new UnauthorizedException("Refresh token reuse detected, please log in again");
    }

    /**
     * Log out THIS device: revoke the token's whole family.
     * Always "succeeds", even for an unknown/expired token: logging out
     * twice is not an error, and we don't reveal which tokens exist.
     */
    @Transactional
    public void logout(String token) {
        try {
            Claims claims = parseRefreshToken(token);
            refreshTokenRepository.findById(UUID.fromString(claims.getId())).ifPresent(row ->
                    refreshTokenRepository.revokeFamily(row.getFamilyId(), LocalDateTime.now(), LOGOUT));
        } catch (UnauthorizedException ignored) {
            // nothing to revoke
        }
    }

    /** Log out EVERY device of this user (e.g. "I lost my phone"). */
    @Transactional
    public int logoutAll(UUID userId) {
        return refreshTokenRepository.revokeAllForUser(userId, LocalDateTime.now(), LOGOUT_ALL);
    }

    // ── helpers ───────────────────────────────────────────────

    private String issue(User user, UUID familyId) {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.save(RefreshToken.builder()
                .id(id)
                .userId(user.getId())
                .familyId(familyId)
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofMillis(jwtUtil.getRefreshTokenExpiryMs())))
                .build());
        return jwtUtil.generateRefreshToken(user.getEmail(), id);
    }

    /** Signature + expiry + "type=refresh" + a valid jti, or 401. */
    private Claims parseRefreshToken(String token) {
        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw invalid(); // bad signature, expired, garbage
        }
        if (!"refresh".equals(claims.get("type", String.class)) || claims.getId() == null) {
            throw invalid(); // an access token, or an old token without an id
        }
        try {
            UUID.fromString(claims.getId());
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        return claims;
    }

    private UnauthorizedException invalid() {
        return new UnauthorizedException("Invalid or expired refresh token");
    }
}
