package com.codeiary.domain.auth.fixture;

import com.codeiary.domain.auth.cookie.TokenCookieManager;
import com.codeiary.domain.auth.provider.JwtTokenProvider;
import java.time.Duration;
import javax.crypto.SecretKey;
import org.springframework.test.util.ReflectionTestUtils;

public final class TokenFixture {
  private TokenFixture() {}

  public static JwtTokenProvider provider(SecretKey secretKey) {
    return provider(secretKey, Duration.ofMinutes(30));
  }

  public static JwtTokenProvider provider(SecretKey secretKey, Duration accessTtl) {
    var provider = new JwtTokenProvider(secretKey);
    ReflectionTestUtils.setField(provider, "issuer", "codeiary");
    ReflectionTestUtils.setField(provider, "audience", "codeiary-api");
    ReflectionTestUtils.setField(provider, "accessTokenTtl", accessTtl);
    ReflectionTestUtils.setField(provider, "refreshTokenTtl", Duration.ofDays(7));
    ReflectionTestUtils.invokeMethod(provider, "initialize");
    return provider;
  }

  public static TokenCookieManager cookies(String domain) {
    var cookies = new TokenCookieManager();
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
