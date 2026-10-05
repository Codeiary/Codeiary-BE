package com.codeiary.global.auth.service;

import com.codeiary.domain.users.entity.User;
import com.codeiary.global.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final SecretKey signingKey;
    private final JwtProperties properties;
    private final Clock authClock;
    private final TokenBlacklistService blacklist;
    private final JwtParser parser;

    public JwtTokenService(SecretKey jwtSigningKey, JwtProperties properties, Clock authClock,
                           TokenBlacklistService blacklist) {
        this.signingKey = jwtSigningKey;
        this.properties = properties;
        this.authClock = authClock;
        this.blacklist = blacklist;
        this.parser = Jwts.parser().verifyWith(jwtSigningKey)
                .requireIssuer(properties.issuer()).requireAudience(properties.audience())
                .require("token_use", "access")
                .clock(() -> Date.from(authClock.instant()))
                .sig().clear().add(Jwts.SIG.HS256).and()
                .build();
    }

    public String issueAccessToken(User user, UUID sessionId, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder().header().type("JWT").and()
                .issuer(properties.issuer()).audience().add(properties.audience()).and()
                .subject(user.getId().toString())
                .issuedAt(Date.from(issuedAt)).notBefore(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId.toString())
                .claim("roles", List.of(user.getRole().name())).claim("token_use", "access")
                .signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    public Claims validateAccessToken(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        if (claims.getExpiration() == null || !claims.getExpiration().toInstant().isAfter(authClock.instant())
                || claims.getSubject() == null || !claims.getSubject().matches("[1-9][0-9]{0,18}")) {
            throw new JwtException("Invalid access token claims");
        }
        String sessionId = claims.get("sid", String.class);
        if (sessionId == null || !sessionId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new JwtException("Invalid access token session");
        }
        if (blacklist.isBlocked(UUID.fromString(sessionId))) {
            throw new JwtException("Revoked access token");
        }
        return claims;
    }
}
