package com.codeiary.global.config;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
public class TokenConfiguration {

    @Value("${token.jwt.secret:}")
    private String configuredSecret;

    @Bean
    SecretKey decodeKey(Environment environment) {
        String secret = configuredSecret;
        if (secret == null || secret.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("운영 환경에는 TOKEN_JWT_SECRET을 설정해야 합니다.");
            }
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            secret = Base64.getEncoder().encodeToString(bytes);
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "token.jwt.secret은 32바이트 이상 Base64 값이어야 합니다.", exception);
        }
        if (key.length < 32) {
            throw new IllegalStateException("token.jwt.secret은 32바이트 이상 Base64 값이어야 합니다.");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }
}
