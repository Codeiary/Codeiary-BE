package com.codeiary.global.security.token.service;

import com.codeiary.global.security.token.fixture.TokenFixture;
import com.codeiary.global.security.token.dto.TokenPair;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.HttpHeaders;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = TokenFixture.cookies("codeiary.com");
    }

    @Test
    @DisplayName("인증 쿠키에 설정값과 토큰 만료 시간을 적용할 수 있다.")
    void writeTokenCookies() {
        // given
        Instant now = Instant.now();
        TokenPair pair = new TokenPair("access-value", "refresh-value",
                now.plus(Duration.ofMinutes(30)), now.plus(Duration.ofDays(7)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        tokenService.writeTokens(response, pair);
        long after = Instant.now().getEpochSecond();

        // then
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).hasSize(2)
                .allSatisfy(cookie -> assertThat(cookie)
                        .contains("Path=/", "Domain=codeiary.com", "HttpOnly", "Secure", "SameSite=Strict"));
        assertThat(response.getCookie("access_token")).isNotNull().satisfies(cookie -> {
            assertThat(cookie.getValue()).isEqualTo(pair.accessToken());
            assertThat((long) cookie.getMaxAge()).isBetween(
                    pair.accessExpiresAt().getEpochSecond() - after, Duration.ofMinutes(30).toSeconds());
        });
        assertThat(response.getCookie("refresh_token")).isNotNull().satisfies(cookie -> {
            assertThat(cookie.getValue()).isEqualTo(pair.refreshToken());
            assertThat((long) cookie.getMaxAge()).isBetween(
                    pair.refreshExpiresAt().getEpochSecond() - after, Duration.ofDays(7).toSeconds());
        });
    }

    @Test
    @DisplayName("로그아웃 쿠키에도 설정값을 적용하고 만료시킬 수 있다.")
    void clearCookies() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        tokenService.clearTokens(response);

        // then
        assertThat(response.getCookies()).extracting(Cookie::getName)
                .containsExactly("access_token", "refresh_token");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).hasSize(2)
                .allSatisfy(cookie -> assertThat(cookie).contains(
                        "=;", "Max-Age=0", "Path=/", "Domain=codeiary.com", "HttpOnly", "Secure", "SameSite=Strict"));
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
    @DisplayName("만료된 토큰의 쿠키를 삭제할 수 있다.")
    void clearExpiredTokenCookies() {
        // given
        Instant expiredAt = Instant.now().minusSeconds(10);
        TokenPair pair = new TokenPair("access-value", "refresh-value",
                expiredAt, expiredAt);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        tokenService.writeTokens(response, pair);

        // then
        assertThat(response.getCookies()).hasSize(2).allSatisfy(cookie ->
                assertThat(cookie.getMaxAge()).isZero());
    }
}
