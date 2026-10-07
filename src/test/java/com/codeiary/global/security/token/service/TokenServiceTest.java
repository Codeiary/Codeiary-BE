package com.codeiary.global.security.token.service;

import com.codeiary.global.security.token.cookie.TokenProperties;
import com.codeiary.global.security.token.dto.TokenPair;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        TokenProperties properties = new TokenProperties(
                "access_token", "refresh_token", "codeiary.com", "/",
                true, true, "Strict", Duration.ofMinutes(30), Duration.ofDays(7));
        tokenService = new TokenService(properties);
    }

    @Test
    @DisplayName("Access Token 쿠키에 설정값을 적용할 수 있다.")
    void createAccessTokenCookie() {
        // when
        ResponseCookie cookie = tokenService.accessToken("access-value");

        // then
        assertThat(cookie.toString())
                .contains("access_token=access-value")
                .contains("Path=/")
                .contains("Domain=codeiary.com")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("로그아웃 쿠키에도 설정값을 적용하고 만료시킬 수 있다.")
    void clearCookies() {
        // when
        ResponseCookie accessCookie = tokenService.clearAccessToken();
        ResponseCookie refreshCookie = tokenService.clearRefreshToken();

        // then
        assertThat(accessCookie.toString()).contains("access_token=").contains("SameSite=Strict");
        assertThat(refreshCookie.toString()).contains("refresh_token=").contains("SameSite=Strict");
        assertThat(accessCookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(refreshCookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("요청 쿠키에서 Access Token을 읽을 수 있다.")
    void readAccessTokenCookie() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "access-value"));

        // when & then
        assertThat(tokenService.accessToken(request)).contains("access-value");
    }

    @Test
    @DisplayName("쿠키 수명을 실제 토큰 만료 시간으로 제한할 수 있다.")
    void capCookieAgeAtTokenExpiry() {
        // given
        Instant expiresAt = Instant.now().plusSeconds(60);
        TokenPair pair = new TokenPair("access-value", "refresh-value", expiresAt, expiresAt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        long before = Instant.now().getEpochSecond();

        // when
        tokenService.writeTokens(response, pair);
        long after = Instant.now().getEpochSecond();

        // then
        assertThat(response.getCookies()).hasSize(2).allSatisfy(cookie ->
                assertThat((long) cookie.getMaxAge()).isBetween(
                        Math.max(0, expiresAt.getEpochSecond() - after),
                        Math.max(0, expiresAt.getEpochSecond() - before)));
    }

    @Test
    @DisplayName("만료된 토큰의 쿠키를 삭제하고 설정된 최대 수명을 지킬 수 있다.")
    void respectCookieAgeBounds() {
        // given
        TokenPair pair = new TokenPair("access-value", "refresh-value",
                Instant.now().minusSeconds(10), Instant.now().plus(Duration.ofDays(8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        tokenService.writeTokens(response, pair);

        // then
        assertThat(response.getCookie("access_token")).isNotNull()
                .satisfies(cookie -> assertThat(cookie.getMaxAge()).isZero());
        assertThat(response.getCookie("refresh_token")).isNotNull()
                .satisfies(cookie -> assertThat(cookie.getMaxAge()).isEqualTo((int) Duration.ofDays(7).toSeconds()));
    }
}
