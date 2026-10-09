package com.codeiary.domain.auth.service;

import com.codeiary.domain.auth.fixture.TokenFixture;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.domain.auth.dto.TokenPair;
import com.codeiary.domain.auth.entity.RefreshToken;
import com.codeiary.domain.auth.entity.TokenBlacklist;
import com.codeiary.domain.auth.exception.TokenErrorCode;
import com.codeiary.domain.auth.provider.JwtTokenProvider;
import com.codeiary.domain.auth.repository.RefreshTokenRepository;
import com.codeiary.domain.auth.repository.TokenBlacklistRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TokenSessionServiceTest {

    @Mock private UserRepository users;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private TokenBlacklistRepository blacklist;
    @Captor private ArgumentCaptor<RefreshToken> savedRefreshToken;
    @Captor private ArgumentCaptor<TokenBlacklist> savedBlacklist;

    private final UUID sessionId = UUID.fromString("d94c78e5-7c18-4d7d-8d48-2b570cc64141");
    private User user;
    private JwtTokenProvider tokens;
    private TokenSessionService sessions;

    @BeforeEach
    void setUp() {
        user = UserFixture.createWithId();
        tokens = TokenFixture.provider(new SecretKeySpec(new byte[32], "HmacSHA256"));
        sessions = new TokenSessionService(users, refreshTokens, blacklist, tokens);
    }

    @Test
    @DisplayName("로그인 시 Refresh Token 원문 대신 해시를 저장할 수 있다.")
    void storeRefreshTokenHash() throws NoSuchAlgorithmException {
        // given
        given(users.findByEmail(UserFixture.EMAIL)).willReturn(Optional.of(user));

        // when
        TokenPair pair = sessions.createSession(UserFixture.EMAIL);

        // then
        then(refreshTokens).should().save(savedRefreshToken.capture());
        RefreshToken saved = savedRefreshToken.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(hash(pair.refreshToken()))
                .isNotEqualTo(pair.refreshToken());
        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getExpiresAt()).isEqualTo(pair.refreshExpiresAt());
        assertThat(saved.getRevokedAt()).isNull();
        assertThat(tokens.validateRefreshToken(pair.refreshToken()).get("sid", String.class))
                .isEqualTo(saved.getSessionId().toString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("없거나 비활성화된 사용자의 로그인을 거절할 수 있다.")
    void rejectUnavailableLoginUser(boolean missingUser) {
        // given
        user.disable();
        given(users.findByEmail(UserFixture.EMAIL))
                .willReturn(missingUser ? Optional.empty() : Optional.of(user));

        // when
        Throwable error = catchThrowable(() -> sessions.createSession(UserFixture.EMAIL));

        // then
        assertTokenError(error, TokenErrorCode.TOKEN_INVALID);
        then(refreshTokens).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("재발급 시 기존 토큰을 폐기하고 최초 만료 시점을 유지할 수 있다.")
    void rotateRefreshToken() throws NoSuchAlgorithmException {
        // given
        Instant expiresAt = Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
        TokenPair original = tokens.generateTokenPair(user.getId(), "ADMIN", sessionId, expiresAt);
        RefreshToken stored = storedToken(original);
        given(users.findByIdForUpdate(user.getId())).willReturn(Optional.of(user));
        given(refreshTokens.findByTokenHash(stored.getTokenHash())).willReturn(Optional.of(stored));

        // when
        TokenPair rotated = sessions.reissue(original.refreshToken());

        // then
        assertThat(stored.getRevokedAt()).isNotNull();
        assertThat(rotated.refreshToken()).isNotEqualTo(original.refreshToken());
        assertThat(rotated.accessToken()).isNotEqualTo(original.accessToken());
        assertThat(rotated.refreshExpiresAt()).isEqualTo(expiresAt);
        assertThat(tokens.validateRefreshToken(rotated.refreshToken()).get("sid", String.class))
                .isEqualTo(sessionId.toString());
        then(users).should().findByIdForUpdate(user.getId());
        then(refreshTokens).should().save(savedRefreshToken.capture());
        assertThat(savedRefreshToken.getValue().getTokenHash()).isEqualTo(hash(rotated.refreshToken()));
        assertThat(savedRefreshToken.getValue().getExpiresAt()).isEqualTo(expiresAt);
        assertThat(savedRefreshToken.getValue().getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("저장되지 않은 Refresh Token의 재발급을 거절할 수 있다.")
    void rejectUnregisteredRefreshToken() {
        // given
        TokenPair pair = tokenPair();
        given(users.findByIdForUpdate(user.getId())).willReturn(Optional.of(user));

        // when
        Throwable error = catchThrowable(() -> sessions.reissue(pair.refreshToken()));

        // then
        assertTokenError(error, TokenErrorCode.TOKEN_INVALID);
        then(refreshTokens).should(never()).save(any(RefreshToken.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("사용자나 로그인 식별자가 다른 토큰의 재발급을 거절할 수 있다.")
    void rejectMismatchedRefreshToken(boolean differentUser) throws NoSuchAlgorithmException {
        // given
        TokenPair pair = tokenPair();
        RefreshToken stored = RefreshToken.create(
                differentUser ? user.getId() + 1 : user.getId(), hash(pair.refreshToken()),
                differentUser ? sessionId : UUID.randomUUID(), pair.refreshExpiresAt());
        given(users.findByIdForUpdate(user.getId())).willReturn(Optional.of(user));
        given(refreshTokens.findByTokenHash(stored.getTokenHash())).willReturn(Optional.of(stored));

        // when
        Throwable error = catchThrowable(() -> sessions.reissue(pair.refreshToken()));

        // then
        assertTokenError(error, TokenErrorCode.TOKEN_INVALID);
        assertThat(stored.getRevokedAt()).isNull();
        then(refreshTokens).should(never()).save(any(RefreshToken.class));
        then(blacklist).should(never()).save(any(TokenBlacklist.class));
    }

    @Test
    @DisplayName("폐기된 Refresh Token을 재사용하면 해당 로그인을 차단할 수 있다.")
    void revokeSessionOnRefreshTokenReuse() throws NoSuchAlgorithmException {
        // given
        TokenPair pair = tokenPair();
        RefreshToken stored = storedToken(pair);
        stored.revoke(Instant.now().minusSeconds(60));
        given(users.findByIdForUpdate(user.getId())).willReturn(Optional.of(user));
        given(refreshTokens.findByTokenHash(stored.getTokenHash())).willReturn(Optional.of(stored));
        given(refreshTokens.findSessionExpiresAt(sessionId)).willReturn(Optional.of(pair.refreshExpiresAt()));

        // when
        Throwable error = catchThrowable(() -> sessions.reissue(pair.refreshToken()));

        // then
        assertTokenError(error, TokenErrorCode.TOKEN_REVOKED);
        then(blacklist).should().save(savedBlacklist.capture());
        assertThat(savedBlacklist.getValue().getSessionId()).isEqualTo(sessionId);
        assertThat(savedBlacklist.getValue().getExpiresAt()).isEqualTo(pair.refreshExpiresAt());
        then(refreshTokens).should().revokeSession(eq(sessionId), any(Instant.class));
        then(refreshTokens).should(never()).save(any(RefreshToken.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("블랙리스트에 등록된 로그인의 인증과 재발급을 거절할 수 있다.")
    void rejectBlacklistedTokens(boolean refresh) {
        // given
        TokenPair pair = tokenPair();
        given(blacklist.existsById(sessionId)).willReturn(true);
        if (refresh) {
            given(users.findByIdForUpdate(user.getId())).willReturn(Optional.of(user));
        }

        // when
        Throwable error = catchThrowable(() -> {
            if (refresh) sessions.reissue(pair.refreshToken());
            else sessions.authenticate(pair.accessToken());
        });

        // then
        assertTokenError(error, TokenErrorCode.TOKEN_REVOKED);
        then(refreshTokens).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Refresh Token으로 로그아웃하고 해당 로그인의 토큰을 폐기할 수 있다.")
    void logoutWithRefreshToken() {
        // given
        TokenPair pair = tokenPair();
        given(users.findByIdForUpdate(user.getId())).willReturn(Optional.of(user));
        given(refreshTokens.findSessionExpiresAt(sessionId)).willReturn(Optional.of(pair.refreshExpiresAt()));

        // when
        sessions.logout(pair.refreshToken(), "invalid-access-token");

        // then
        then(blacklist).should().save(savedBlacklist.capture());
        assertThat(savedBlacklist.getValue().getSessionId()).isEqualTo(sessionId);
        assertThat(savedBlacklist.getValue().getExpiresAt()).isEqualTo(pair.refreshExpiresAt());
        then(refreshTokens).should().revokeSession(eq(sessionId), any(Instant.class));
    }

    @Test
    @DisplayName("Access Token으로 로그아웃해도 기존 차단 기간을 유지할 수 있다.")
    void logoutWithAccessToken() {
        // given
        TokenPair pair = tokenPair();
        Instant existingExpiry = pair.refreshExpiresAt().plusSeconds(60);
        given(users.findByIdForUpdate(user.getId())).willReturn(Optional.of(user));
        given(refreshTokens.findSessionExpiresAt(sessionId)).willReturn(Optional.of(pair.refreshExpiresAt()));
        given(blacklist.findById(sessionId)).willReturn(Optional.of(TokenBlacklist.create(sessionId, existingExpiry)));

        // when
        sessions.logout("invalid-refresh-token", pair.accessToken());

        // then
        then(blacklist).should().save(savedBlacklist.capture());
        assertThat(savedBlacklist.getValue().getSessionId()).isEqualTo(sessionId);
        assertThat(savedBlacklist.getValue().getExpiresAt()).isEqualTo(existingExpiry);
        then(refreshTokens).should().revokeSession(eq(sessionId), any(Instant.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "invalid-token")
    @DisplayName("유효한 토큰이 없어도 로그아웃을 완료할 수 있다.")
    void logoutWithoutValidTokens(String token) {
        // given
        String refreshToken = token;
        String accessToken = token;

        // when
        Throwable error = catchThrowable(() -> sessions.logout(refreshToken, accessToken));

        // then
        assertThat(error).isNull();
        then(users).shouldHaveNoInteractions();
        then(refreshTokens).shouldHaveNoInteractions();
        then(blacklist).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인증 시 저장소의 현재 사용자와 권한을 조회할 수 있다.")
    void authenticateCurrentUser() {
        // given
        TokenPair pair = tokens.generateTokenPair(user.getId(), "USER", sessionId);
        given(users.findById(user.getId())).willReturn(Optional.of(user));

        // when
        User authenticated = sessions.authenticate(pair.accessToken());

        // then
        assertThat(authenticated).isSameAs(user);
        assertThat(authenticated.getRole()).isEqualTo(Role.ADMIN);
        then(refreshTokens).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    @DisplayName("없거나 비활성화된 사용자의 인증과 재발급을 거절할 수 있다.")
    void rejectUnavailableAuthenticatedUser(boolean refresh, boolean missingUser) {
        // given
        TokenPair pair = tokenPair();
        user.disable();
        Optional<User> result = missingUser ? Optional.empty() : Optional.of(user);
        if (refresh) given(users.findByIdForUpdate(user.getId())).willReturn(result);
        else given(users.findById(user.getId())).willReturn(result);

        // when
        Throwable error = catchThrowable(() -> {
            if (refresh) sessions.reissue(pair.refreshToken());
            else sessions.authenticate(pair.accessToken());
        });

        // then
        assertTokenError(error, TokenErrorCode.TOKEN_INVALID);
        then(refreshTokens).shouldHaveNoInteractions();
        then(blacklist).should(never()).save(any(TokenBlacklist.class));
    }

    private TokenPair tokenPair() {
        return tokens.generateTokenPair(user.getId(), user.getRole().name(), sessionId);
    }

    private RefreshToken storedToken(TokenPair pair) throws NoSuchAlgorithmException {
        return RefreshToken.create(user.getId(), hash(pair.refreshToken()), sessionId, pair.refreshExpiresAt());
    }

    private String hash(String token) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private void assertTokenError(Throwable error, TokenErrorCode code) {
        assertThat(error).isInstanceOfSatisfying(RestApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
