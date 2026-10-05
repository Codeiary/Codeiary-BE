package com.codeiary.global.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth.jwt")
public record JwtProperties(String secret, String issuer, String audience,
                            Duration accessTokenTtl, Duration refreshTokenTtl) {

    public JwtProperties {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer와 audience는 필수입니다.");
        }
        if (accessTokenTtl == null || accessTokenTtl.toSeconds() < 1
                || refreshTokenTtl == null || refreshTokenTtl.compareTo(accessTokenTtl) <= 0) {
            throw new IllegalArgumentException("JWT 만료 시간은 양수이고 Refresh Token이 더 길어야 합니다.");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", secret=REDACTED]";
    }
}
