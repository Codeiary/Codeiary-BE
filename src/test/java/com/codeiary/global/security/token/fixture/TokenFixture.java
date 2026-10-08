package com.codeiary.global.security.token.fixture;

import com.codeiary.global.security.token.provider.JwtTokenProvider;
import com.codeiary.global.security.token.service.TokenService;
import java.time.Duration;
import javax.crypto.SecretKey;
import org.springframework.test.util.ReflectionTestUtils;

public final class TokenFixture {
    private TokenFixture() {}

    public static JwtTokenProvider provider(SecretKey key) {
        return provider(key, Duration.ofMinutes(30));
    }

    public static JwtTokenProvider provider(SecretKey key, Duration accessTtl) {
        var provider = new JwtTokenProvider(key);
        ReflectionTestUtils.setField(provider, "issuer", "codeiary");
        ReflectionTestUtils.setField(provider, "audience", "codeiary-api");
        ReflectionTestUtils.setField(provider, "accessTokenTtl", accessTtl);
        ReflectionTestUtils.setField(provider, "refreshTokenTtl", Duration.ofDays(7));
        ReflectionTestUtils.invokeMethod(provider, "initialize");
        return provider;
    }

    public static TokenService cookies(String domain) {
        var cookies = new TokenService();
        ReflectionTestUtils.setField(cookies, "accessName", "access_token");
        ReflectionTestUtils.setField(cookies, "refreshName", "refresh_token");
        ReflectionTestUtils.setField(cookies, "domain", domain);
        ReflectionTestUtils.setField(cookies, "path", "/");
        ReflectionTestUtils.setField(cookies, "secure", true);
        ReflectionTestUtils.setField(cookies, "httpOnly", true);
        ReflectionTestUtils.setField(cookies, "sameSite", "Strict");
        return cookies;
    }
}
