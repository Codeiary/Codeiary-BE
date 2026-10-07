package com.codeiary.global.security.token.provider;

import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.token.exception.TokenErrorCode;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private final SecretKey signingKey = new SecretKeySpec(new byte[32], "HmacSHA256");
    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(properties(), signingKey);
    }

    @Test
    @DisplayName("Access Token을 발급하고 검증할 수 있다.")
    void issueAndValidateAccessToken() {
        // given
        UUID sessionId = UUID.randomUUID();

        // when
        String token = tokenProvider.generateAccessToken(1L, "ADMIN", sessionId);
        Claims claims = tokenProvider.validateAccessToken(token);

        // then
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.get("sid", String.class)).isEqualTo(sessionId.toString());
        assertThat(claims.get("token_use", String.class)).isEqualTo("access");
    }

    @Test
    @DisplayName("Refresh Token을 발급하고 검증할 수 있다.")
    void issueAndValidateRefreshToken() {
        // given
        UUID sessionId = UUID.randomUUID();

        // when
        String token = tokenProvider.generateRefreshToken(2L, "USER", sessionId);
        Claims claims = tokenProvider.validateRefreshToken(token);

        // then
        assertThat(claims.getSubject()).isEqualTo("2");
        assertThat(claims.get("token_use", String.class)).isEqualTo("refresh");
    }

    @Test
    @DisplayName("Access Token을 Refresh Token으로 검증할 수 없다.")
    void rejectAccessTokenAsRefreshToken() {
        // given
        String accessToken = tokenProvider.generateAccessToken(1L, "USER", UUID.randomUUID());

        // when & then
        assertThatThrownBy(() -> tokenProvider.validateRefreshToken(accessToken))
                .isInstanceOf(RestApiException.class)
                .extracting(exception -> ((RestApiException) exception).getErrorCode())
                .isEqualTo(TokenErrorCode.TOKEN_TYPE_MISMATCH);
    }

    @Test
    @DisplayName("만료된 토큰을 거절할 수 있다.")
    void rejectExpiredToken() {
        // given
        JwtTokenProvider expiredTokenProvider = new JwtTokenProvider(
                new TokenJwtProperties("unused", "codeiary", "codeiary-api",
                        Duration.ofSeconds(-1), Duration.ofDays(7)), signingKey);
        String token = expiredTokenProvider.generateAccessToken(1L, "USER", UUID.randomUUID());

        // when & then
        assertThatThrownBy(() -> tokenProvider.validateAccessToken(token))
                .isInstanceOf(RestApiException.class)
                .extracting(exception -> ((RestApiException) exception).getErrorCode())
                .isEqualTo(TokenErrorCode.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("토큰이 없으면 인증 오류를 반환할 수 있다.")
    void rejectMissingToken() {
        // when & then
        assertThatThrownBy(() -> tokenProvider.validateAccessToken(null))
                .isInstanceOf(RestApiException.class)
                .extracting(exception -> ((RestApiException) exception).getErrorCode())
                .isEqualTo(TokenErrorCode.TOKEN_MISSING);
    }

    private TokenJwtProperties properties() {
        return new TokenJwtProperties("unused", "codeiary", "codeiary-api",
                Duration.ofMinutes(30), Duration.ofDays(7));
    }
}
