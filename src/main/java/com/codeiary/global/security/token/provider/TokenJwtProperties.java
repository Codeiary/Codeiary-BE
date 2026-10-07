package com.codeiary.global.security.token.provider;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("token.jwt")
public record TokenJwtProperties(
        String secret,
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}
