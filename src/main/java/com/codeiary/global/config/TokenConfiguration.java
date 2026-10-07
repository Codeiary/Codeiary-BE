package com.codeiary.global.config;

import com.codeiary.global.security.token.cookie.TokenProperties;
import com.codeiary.global.security.token.provider.TokenJwtProperties;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({TokenJwtProperties.class, TokenProperties.class})
public class TokenConfiguration {

    @Bean
    SecretKey decodeKey(TokenJwtProperties properties, Environment environment) {
        String secret = properties.secret();
        if (secret == null || secret.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("운영 환경에는 TOKEN_JWT_SECRET을 설정해야 합니다.");
            }
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            secret = Base64.getEncoder().encodeToString(bytes);
        }
        try {
            byte[] key = Base64.getDecoder().decode(secret);
            if (key.length < 32) {
                throw new IllegalArgumentException();
            }
            return new SecretKeySpec(key, "HmacSHA256");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "token.jwt.secret은 32바이트 이상 Base64 값이어야 합니다.", exception);
        }
    }
}
