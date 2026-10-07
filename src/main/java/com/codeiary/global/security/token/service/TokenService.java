package com.codeiary.global.security.token.service;

import com.codeiary.global.security.token.dto.TokenPair;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.codeiary.global.security.token.cookie.TokenProperties;

@Service
public class TokenService {

    private final TokenProperties properties;

    public TokenService(TokenProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie accessToken(String token) {
        return create(properties.accessName(), token, properties.accessMaxAge());
    }

    public ResponseCookie refreshToken(String token) {
        return create(properties.refreshName(), token, properties.refreshMaxAge());
    }

    public ResponseCookie clearAccessToken() {
        return create(properties.accessName(), "", Duration.ZERO);
    }

    public ResponseCookie clearRefreshToken() {
        return create(properties.refreshName(), "", Duration.ZERO);
    }

    public Optional<String> accessToken(HttpServletRequest request) {
        return find(request, properties.accessName());
    }

    public Optional<String> refreshToken(HttpServletRequest request) {
        return find(request, properties.refreshName());
    }

    public void writeTokens(HttpServletResponse response, TokenPair pair) {
        response.addHeader(HttpHeaders.SET_COOKIE, create(properties.accessName(), pair.accessToken(),
                remainingAge(pair.accessExpiresAt(), properties.accessMaxAge())).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, create(properties.refreshName(), pair.refreshToken(),
                remainingAge(pair.refreshExpiresAt(), properties.refreshMaxAge())).toString());
    }

    public void clearTokens(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, clearAccessToken().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefreshToken().toString());
    }

    private Duration remainingAge(Instant expiresAt, Duration configuredMaxAge) {
        Duration remaining = Duration.ofSeconds(Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond()));
        return remaining.compareTo(configuredMaxAge) < 0 ? remaining : configuredMaxAge;
    }

    private ResponseCookie create(String name, String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(name, value)
                .httpOnly(properties.httpOnly())
                .secure(properties.secure())
                .path(properties.path())
                .sameSite(properties.sameSite())
                .maxAge(maxAge);
        if (StringUtils.hasText(properties.domain())) cookie.domain(properties.domain());
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
