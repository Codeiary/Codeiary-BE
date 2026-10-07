package com.codeiary.global.security.token.provider;

import com.codeiary.global.security.token.exception.TokenErrorCode;
import com.codeiary.global.security.token.dto.TokenPair;
import com.codeiary.global.exception.RestApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public final class JwtTokenProvider {

    private final TokenJwtProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public JwtTokenProvider(TokenJwtProperties properties, SecretKey signingKey) {
        this.properties = properties;
        this.clock = Clock.systemUTC();
        this.signingKey = signingKey;
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .requireAudience(properties.audience())
                .clock(() -> Date.from(clock.instant()))
                .sig().clear().add(Jwts.SIG.HS256).and()
                .build();
    }

    public String generateAccessToken(Long userId, String role, UUID sessionId) {
        return generate(userId, role, sessionId, "access", clock.instant().plus(properties.accessTokenTtl()));
    }

    public String generateRefreshToken(Long userId, String role, UUID sessionId) {
        return generate(userId, role, sessionId, "refresh", clock.instant().plus(properties.refreshTokenTtl()));
    }

    public TokenPair generateTokenPair(Long userId, String role, UUID sessionId) {
        return generateTokenPair(userId, role, sessionId,
                clock.instant().plus(properties.refreshTokenTtl()).truncatedTo(ChronoUnit.SECONDS));
    }

    public TokenPair generateTokenPair(Long userId, String role, UUID sessionId, Instant refreshExpiresAt) {
        Instant now = clock.instant();
        if (!refreshExpiresAt.isAfter(now)) {
            throw new RestApiException(TokenErrorCode.TOKEN_EXPIRED);
        }
        Instant accessExpiresAt = now.plus(properties.accessTokenTtl()).truncatedTo(ChronoUnit.SECONDS);
        if (accessExpiresAt.isAfter(refreshExpiresAt)) {
            accessExpiresAt = refreshExpiresAt;
        }
        return new TokenPair(
                generate(userId, role, sessionId, "access", accessExpiresAt),
                generate(userId, role, sessionId, "refresh", refreshExpiresAt),
                accessExpiresAt, refreshExpiresAt);
    }

    public Claims validateAccessToken(String token) {
        return validate(token, "access");
    }

    public Claims validateRefreshToken(String token) {
        return validate(token, "refresh");
    }

    private String generate(Long userId, String role, UUID sessionId,
                            String tokenUse, Instant expiresAt) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .header().type("JWT").and()
                .issuer(properties.issuer())
                .audience().add(properties.audience()).and()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(issuedAt))
                .notBefore(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId.toString())
                .claim("role", role)
                .claim("token_use", tokenUse)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private Claims validate(String token, String expectedUse) {
        if (token == null || token.isBlank()) {
            throw new RestApiException(TokenErrorCode.TOKEN_MISSING);
        }
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            if (!expectedUse.equals(claims.get("token_use", String.class))) {
                throw new RestApiException(TokenErrorCode.TOKEN_TYPE_MISMATCH);
            }
            String subject = claims.getSubject();
            String sessionId = claims.get("sid", String.class);
            if (subject == null || sessionId == null || claims.getExpiration() == null
                    || claims.getId() == null || claims.getId().isBlank()) {
                throw new RestApiException(TokenErrorCode.TOKEN_INVALID);
            }
            Long.parseLong(subject);
            UUID.fromString(sessionId);
            return claims;
        } catch (ExpiredJwtException exception) {
            throw new RestApiException(TokenErrorCode.TOKEN_EXPIRED, exception);
        } catch (RestApiException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new RestApiException(TokenErrorCode.TOKEN_INVALID, exception);
        }
    }
}
