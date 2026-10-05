package com.codeiary.global.auth.config;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecretKey jwtSigningKey(JwtProperties properties, Environment environment) {
        byte[] key;
        if (properties.secret() == null || properties.secret().isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))
                    || !environment.acceptsProfiles(Profiles.of("local", "test"))) {
                throw new IllegalStateException("운영 환경에는 JWT_SECRET을 설정해야 합니다.");
            }
            key = new byte[32];
            new SecureRandom().nextBytes(key);
        } else {
            try {
                key = Base64.getDecoder().decode(properties.secret());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("JWT_SECRET은 Base64 형식이어야 합니다.");
            }
            if (key.length < 32) {
                throw new IllegalStateException("JWT_SECRET은 디코딩 후 최소 32바이트여야 합니다.");
            }
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

}
