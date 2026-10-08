package com.codeiary.domain.auth.cookie;

import com.codeiary.domain.auth.dto.TokenPair;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TokenCookieManager {

    @Value("${token.cookie.access-name}")
    private String accessName;

    @Value("${token.cookie.refresh-name}")
    private String refreshName;

    @Value("${token.cookie.domain:}")
    private String domain;

    @Value("${token.cookie.path}")
    private String path;

    @Value("${token.cookie.secure}")
    private boolean secure;

    @Value("${token.cookie.http-only}")
    private boolean httpOnly;

    @Value("${token.cookie.same-site}")
    private String sameSite;

    public Optional<String> accessToken(HttpServletRequest request) {
        return find(request, accessName);
    }

    public Optional<String> refreshToken(HttpServletRequest request) {
        return find(request, refreshName);
    }

    public void writeTokens(HttpServletResponse response, TokenPair pair) {
        response.addHeader(HttpHeaders.SET_COOKIE, create(accessName, pair.accessToken(),
                remainingAge(pair.accessExpiresAt())).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, create(refreshName, pair.refreshToken(),
                remainingAge(pair.refreshExpiresAt())).toString());
    }

    public void clearTokens(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, create(accessName, "", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, create(refreshName, "", Duration.ZERO).toString());
    }

    private Duration remainingAge(Instant expiresAt) {
        return Duration.ofSeconds(Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond()));
    }

    private ResponseCookie create(String name, String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(secure)
                .path(path)
                .sameSite(sameSite)
                .maxAge(maxAge);
        if (StringUtils.hasText(domain)) cookie.domain(domain);
        return cookie.build();
    }

    private Optional<String> find(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst();
    }
}
