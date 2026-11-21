package com.fooddelivery.backend.unit;

import com.fooddelivery.backend.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtUtil normally gets its secret via @Value. Without Spring we set the
 * private fields ourselves with ReflectionTestUtils.setField().
 */
class JwtUtilTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private JwtUtil jwt;

    @BeforeEach
    void setUp() {
        jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", SECRET);
        ReflectionTestUtils.setField(jwt, "accessTokenExpiryMs", 60_000L);
        ReflectionTestUtils.setField(jwt, "refreshTokenExpiryMs", 600_000L);
    }

    @Test
    void accessTokenCarriesEmailAndRole() {
        String token = jwt.generateAccessToken("a@b.com", "CUSTOMER");

        assertThat(jwt.extractEmail(token)).isEqualTo("a@b.com");
        assertThat(jwt.extractRole(token)).isEqualTo("CUSTOMER");
        assertThat(jwt.isTokenValid(token, "a@b.com")).isTrue();
        assertThat(jwt.isAccessToken(token)).isTrue();
    }

    @Test
    void refreshTokenIsNotAnAccessToken() {
        // The security fix: a 7-day refresh token must not work as a login token
        String refresh = jwt.generateRefreshToken("a@b.com", UUID.randomUUID());
        assertThat(jwt.isAccessToken(refresh)).isFalse();
    }

    @Test
    void refreshTokenCarriesItsDatabaseId() {
        UUID id = UUID.randomUUID();
        var claims = jwt.parse(jwt.generateRefreshToken("a@b.com", id));
        assertThat(claims.getId()).isEqualTo(id.toString());
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
    }

    @Test
    void tokenForSomeoneElseIsNotValidForMe() {
        String token = jwt.generateAccessToken("a@b.com", "CUSTOMER");
        assertThat(jwt.isTokenValid(token, "other@b.com")).isFalse();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwt.generateAccessToken("a@b.com", "CUSTOMER");
        // flip one character in the signature part
        String tampered = token.substring(0, token.length() - 2)
                + (token.charAt(token.length() - 2) == 'A' ? 'B' : 'A') + token.charAt(token.length() - 1);

        assertThatThrownBy(() -> jwt.extractEmail(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        ReflectionTestUtils.setField(jwt, "accessTokenExpiryMs", -1_000L); // already expired
        String token = jwt.generateAccessToken("a@b.com", "CUSTOMER");

        assertThatThrownBy(() -> jwt.extractEmail(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtUtil attacker = new JwtUtil();
        ReflectionTestUtils.setField(attacker, "secret", SECRET.replace('4', '5'));
        ReflectionTestUtils.setField(attacker, "accessTokenExpiryMs", 60_000L);
        String forged = attacker.generateAccessToken("admin@b.com", "ADMIN");

        assertThatThrownBy(() -> jwt.extractEmail(forged)).isInstanceOf(JwtException.class);
    }
}
