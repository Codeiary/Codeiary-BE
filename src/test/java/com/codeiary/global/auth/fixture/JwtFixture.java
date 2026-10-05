package com.codeiary.global.auth.fixture;

import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.global.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.mock.env.MockEnvironment;

public final class JwtFixture {

    public static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");
    public static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    public static final Duration REFRESH_TTL = Duration.ofDays(7);
    public static final UUID SESSION_ID = UUID.fromString("ef94c2b1-13e9-490c-a6f4-50a718b9532c");

    private JwtFixture() {
    }

    public static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    public static JwtProperties properties() {
        return properties("");
    }

    public static JwtProperties properties(String secret) {
        return new JwtProperties(secret, "codeiary", "codeiary-api", ACCESS_TTL, REFRESH_TTL);
    }

    public static SecretKey signingKey() {
        return Jwts.SIG.HS256.key().build();
    }

    public static JwtBuilder accessClaims() {
        return Jwts.builder().issuer("codeiary").audience().add("codeiary-api").and()
                .subject(Long.toString(UserFixture.ID))
                .claim("sid", SESSION_ID.toString())
                .issuedAt(Date.from(NOW)).notBefore(Date.from(NOW))
                .expiration(Date.from(NOW.plus(ACCESS_TTL)))
                .claim("roles", List.of("ADMIN")).claim("token_use", "access");
    }

    public static Claims claims(String role) {
        return claims(Long.toString(UserFixture.ID), role);
    }

    public static Claims claims(String subject, String role) {
        return Jwts.claims().subject(subject).add("roles", List.of(role)).build();
    }

    public static String sign(SecretKey key, JwtBuilder claims) {
        return claims.signWith(key, Jwts.SIG.HS256).compact();
    }

    public static String base64Secret(int byteLength) {
        return Base64.getEncoder().encodeToString(new byte[byteLength]);
    }

    public static MockEnvironment environment(String... profiles) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }
}
