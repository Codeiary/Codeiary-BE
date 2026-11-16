package com.codeiary.domain.auth.service;

import com.codeiary.domain.auth.dto.TokenPair;
import com.codeiary.domain.auth.fixture.TokenFixture;
import com.codeiary.domain.auth.provider.JwtTokenProvider;
import com.codeiary.domain.auth.repository.RedisTokenRepository;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.RestApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock private UserRepository users;
    @Mock private RedisTokenRepository tokenRepository;

    private final UUID loginId = UUID.fromString("d94c78e5-7c18-4d7d-8d48-2b570cc64141");
    private User user;
    private JwtTokenProvider tokens;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        user = UserFixture.createWithId();
        tokens = TokenFixture.provider(new SecretKeySpec(new byte[32], "HmacSHA256"));
        tokenService = new TokenService(users, tokenRepository, tokens);
    }

    @Test
    @DisplayName("로그인 시 리프레시 토큰 원문 대신 해시를 Redis에 저장할 수 있다.")
    void storeRefreshTokenHash() throws NoSuchAlgorithmException {
        given(users.findByEmail(UserFixture.EMAIL)).willReturn(Optional.of(user));
        given(tokenRepository.save(eq(user.getId()), any(UUID.class), any(String.class), any(Instant.class)))
                .willReturn(1L);

        TokenPair pair = tokenService.login(UserFixture.EMAIL);

        UUID loginId = UUID.fromString(tokens.validateRefreshToken(pair.refreshToken()).get("sid", String.class));
        then(tokenRepository).should().save(user.getId(), loginId, hash(pair.refreshToken()), pair.refreshExpiresAt());
    }

    @Test
    @DisplayName("리프레시 토큰 저장이 거부되면 로그인 토큰 발급을 중단할 수 있다.")
    void rejectFailedRefreshTokenSave() {
        given(users.findByEmail(UserFixture.EMAIL)).willReturn(Optional.of(user));
        given(tokenRepository.save(eq(user.getId()), any(UUID.class), any(String.class), any(Instant.class)))
                .willReturn(0L);

        Throwable error = catchThrowable(() -> tokenService.login(UserFixture.EMAIL));

        assertThat(error).isInstanceOfSatisfying(RestApiException.class,
                exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
                    assertThat(exception.getCause()).hasMessage("Redis token save returned 0");
                });
    }

    @Test
    @DisplayName("재발급 시 최초 만료 시각을 유지할 수 있다.")
    void rotateRefreshToken() throws NoSuchAlgorithmException {
        Instant expiresAt = Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
        TokenPair original = tokens.generateTokenPair(user.getId(), "ADMIN", loginId, expiresAt);
        given(users.findById(user.getId())).willReturn(Optional.of(user));
        given(tokenRepository.rotate(eq(user.getId()), eq(loginId), eq(hash(original.refreshToken())),
                any(String.class), eq(expiresAt))).willReturn(RedisTokenRepository.Rotation.ROTATED);

        TokenPair rotated = tokenService.reissue(original.refreshToken());

        assertThat(rotated.refreshExpiresAt()).isEqualTo(expiresAt);
        then(tokenRepository).should().rotate(user.getId(), loginId,
                hash(original.refreshToken()), hash(rotated.refreshToken()), expiresAt);
    }

    @Test
    @DisplayName("블랙리스트 조회 결과가 없으면 인증을 거절할 수 있다.")
    void rejectMissingBlacklistResult() {
        TokenPair pair = tokenPair();
        given(tokenRepository.isBlacklisted(loginId)).willReturn(Optional.empty());

        Throwable error = catchThrowable(() -> tokenService.authenticate(pair.accessToken()));

        assertThat(error).isInstanceOfSatisfying(RestApiException.class,
                exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
                    assertThat(exception.getCause()).hasMessage("Redis blacklist lookup returned null");
                });
    }

    @Test
    @DisplayName("Access Token만 있어도 로그아웃할 수 있다.")
    void logoutWithAccessToken() {
        TokenPair pair = tokenPair();
        given(tokenRepository.revoke(loginId, pair.accessExpiresAt())).willReturn(1L);

        tokenService.logout("invalid-refresh", pair.accessToken());

        then(tokenRepository).should().revoke(loginId, pair.accessExpiresAt());
    }

    @Test
    @DisplayName("블랙리스트 등록이 실패하면 로그아웃 오류를 반환할 수 있다.")
    void rejectFailedLogout() {
        TokenPair pair = tokenPair();
        given(tokenRepository.revoke(loginId, pair.accessExpiresAt())).willReturn(0L);

        Throwable error = catchThrowable(() -> tokenService.logout("invalid-refresh", pair.accessToken()));

        assertThat(error).isInstanceOfSatisfying(RestApiException.class,
                exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
                    assertThat(exception.getCause()).hasMessage("Redis token revoke returned 0");
                });
    }

    @Test
    @DisplayName("유효한 토큰이 없어도 로그아웃 요청을 완료할 수 있다.")
    void logoutWithoutValidToken() {
        tokenService.logout("invalid-refresh", "invalid-access");

        then(tokenRepository).shouldHaveNoInteractions();
    }

    private TokenPair tokenPair() {
        return tokens.generateTokenPair(user.getId(), user.getRole().name(), loginId);
    }

    private String hash(String token) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }

}
