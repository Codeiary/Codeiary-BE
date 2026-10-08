package com.codeiary.domain.auth.provider;

import com.codeiary.domain.auth.fixture.TokenFixture;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.domain.auth.dto.TokenPair;
import com.codeiary.domain.auth.exception.TokenErrorCode;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        tokenProvider = TokenFixture.provider(signingKey);
    }

    @Test
    @DisplayName("Access Token을 사용한 재발급을 거절할 수 있다.")
    void rejectAccessTokenAsRefreshToken() {
        // given
        String accessToken = tokenProvider.generateTokenPair(1L, "USER", UUID.randomUUID()).accessToken();

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
        JwtTokenProvider expiredTokenProvider = TokenFixture.provider(signingKey, Duration.ofSeconds(-1));
        String token = expiredTokenProvider.generateTokenPair(1L, "USER", UUID.randomUUID()).accessToken();

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

    @Test
    @DisplayName("다른 키로 서명한 토큰을 거절할 수 있다.")
    void rejectDifferentSigningKey() {
        // given
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        JwtTokenProvider otherProvider = TokenFixture.provider(new SecretKeySpec(otherKey, "HmacSHA256"));
        String token = otherProvider.generateTokenPair(1L, "ADMIN", UUID.randomUUID()).accessToken();

        // when & then
        assertThatThrownBy(() -> tokenProvider.validateAccessToken(token))
                .isInstanceOf(RestApiException.class)
                .extracting(exception -> ((RestApiException) exception).getErrorCode())
                .isEqualTo(TokenErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("Access Token 30분과 Refresh Token 7일의 만료 시간을 설정할 수 있다.")
    void issueTokenPairWithConfiguredExpiry() {
        // given
        UUID sessionId = UUID.randomUUID();
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // when
        TokenPair pair = tokenProvider.generateTokenPair(1L, "USER", sessionId);
        Instant after = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Claims access = tokenProvider.validateAccessToken(pair.accessToken());
        Claims refresh = tokenProvider.validateRefreshToken(pair.refreshToken());

        // then
        for (Claims claims : new Claims[]{access, refresh}) {
            assertThat(claims.getSubject()).isEqualTo("1");
            assertThat(claims.get("role", String.class)).isEqualTo("USER");
            assertThat(claims.get("sid", String.class)).isEqualTo(sessionId.toString());
        }
        assertThat(access.get("token_use", String.class)).isEqualTo("access");
        assertThat(refresh.get("token_use", String.class)).isEqualTo("refresh");
        assertThat(pair.accessExpiresAt()).isBetween(before.plus(Duration.ofMinutes(30)),
                after.plus(Duration.ofMinutes(30)));
        assertThat(pair.refreshExpiresAt()).isBetween(before.plus(Duration.ofDays(7)),
                after.plus(Duration.ofDays(7)));
        assertThat(access.getExpiration().toInstant()).isEqualTo(pair.accessExpiresAt());
        assertThat(refresh.getExpiration().toInstant()).isEqualTo(pair.refreshExpiresAt());
    }

    @Test
    @DisplayName("Access Token의 만료를 남은 로그인 시간으로 제한할 수 있다.")
    void capAccessTokenExpiryAtSessionExpiry() {
        // given
        Instant sessionExpiresAt = Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.SECONDS);

        // when
        TokenPair pair = tokenProvider.generateTokenPair(1L, "USER", UUID.randomUUID(), sessionExpiresAt);

        // then
        assertThat(pair.accessExpiresAt()).isEqualTo(sessionExpiresAt);
        assertThat(pair.refreshExpiresAt()).isEqualTo(sessionExpiresAt);
        assertThat(tokenProvider.validateAccessToken(pair.accessToken()).getExpiration().toInstant())
                .isEqualTo(sessionExpiresAt);
    }

}
