package com.codeiary.domain.auth.cookie;

import com.codeiary.domain.auth.fixture.TokenFixture;
import com.codeiary.domain.auth.dto.TokenPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCookieManagerTest {

    private TokenCookieManager cookies;

    @BeforeEach
    void setUp() {
        cookies = TokenFixture.cookies("codeiary.com");
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
        cookies.writeTokens(response, pair);
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
        cookies.writeTokens(response, pair);

        // then
        assertThat(response.getCookies()).hasSize(2).allSatisfy(cookie ->
                assertThat(cookie.getMaxAge()).isZero());
    }
}
