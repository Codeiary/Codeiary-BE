package com.codeiary.global.security.token.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
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
