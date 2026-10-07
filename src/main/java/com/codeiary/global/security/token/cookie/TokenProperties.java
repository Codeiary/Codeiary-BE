package com.codeiary.global.security.token.cookie;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("token.cookie")
public record TokenProperties(
        String accessName,
        String refreshName,
        String domain,
        String path,
        boolean secure,
        boolean httpOnly,
        String sameSite,
        Duration accessMaxAge,
        Duration refreshMaxAge
) {
}
