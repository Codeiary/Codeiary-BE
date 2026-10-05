package com.codeiary.global.auth.service;

import com.codeiary.domain.users.dto.UserMapper;
import com.codeiary.global.auth.dto.AuthMapper;
import com.codeiary.global.auth.entity.RefreshToken;
import com.codeiary.domain.users.entity.User;
import com.codeiary.domain.users.entity.enums.Role;
import com.codeiary.global.auth.exception.AuthErrorCode;
import com.codeiary.global.auth.fixture.AuthRequestFixture;
import com.codeiary.global.auth.fixture.AuthResponseFixture;
import com.codeiary.global.auth.fixture.RefreshTokenFixture;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.global.auth.repository.RefreshTokenRepository;
import com.codeiary.domain.users.repository.UserRepository;
import com.codeiary.global.exception.ErrorCode;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.auth.fixture.JwtFixture;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static com.codeiary.domain.users.fixture.UserFixture.EMAIL;
import static com.codeiary.domain.users.fixture.UserFixture.PASSWORD;
import static com.codeiary.global.auth.fixture.JwtFixture.NOW;
import static com.codeiary.global.auth.fixture.RefreshTokenFixture.RAW_TOKEN;
import static com.codeiary.global.auth.fixture.RefreshTokenFixture.TOKEN_HASH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@SpringJUnitConfig(AuthServiceTest.MapperConfiguration.class)
class AuthServiceTest {

    @Mock private UserRepository users;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private JwtTokenService jwtTokens;
    @Mock private TokenBlacklistService blacklist;
    private AuthService service;
    private User user;
    @Autowired private AuthMapper authMapper;

    @BeforeEach
    void setUp() {
        user = UserFixture.createWithId();
        service = new AuthService(users, refreshTokens, UserFixture.passwordEncoder(), jwtTokens, blacklist,
                JwtFixture.properties(), JwtFixture.fixedClock(), authMapper);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("로그인으로 토큰을 발급할 수 있다.")
    void login(Role role) {
        // given
        user = UserFixture.createWithId(role);
        var request = AuthRequestFixture.login();
        given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));
        given(jwtTokens.issueAccessToken(eq(user), any(UUID.class), eq(NOW), eq(NOW.plus(JwtFixture.ACCESS_TTL))))
                .willReturn(AuthResponseFixture.ACCESS_TOKEN);

        // when
        var response = service.login(request);

        // then
        assertThat(response.accessToken()).isEqualTo(AuthResponseFixture.ACCESS_TOKEN);
        assertThat(response.refreshToken()).hasSize(43);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800);
        assertThat(response.refreshExpiresIn()).isEqualTo(604800);
        assertThat(response.user()).isEqualTo(UserFixture.response(role));
        var stored = ArgumentCaptor.forClass(RefreshToken.class);
        then(refreshTokens).should().save(stored.capture());
        assertThat(stored.getValue().getTokenHash()).isEqualTo(RefreshTokenFixture.hashOf(response.refreshToken()));
        assertThat(stored.getValue().getTokenHash()).isNotEqualTo(response.refreshToken());
        assertThat(stored.getValue().getExpiresAt()).isEqualTo(NOW.plus(JwtFixture.REFRESH_TTL));
        then(jwtTokens).should().issueAccessToken(user, stored.getValue().getSessionId(), NOW, NOW.plus(JwtFixture.ACCESS_TTL));
        then(users).should().findByEmail(EMAIL);
    }

    @Test
    @DisplayName("잘못된 계정 정보를 거절할 수 있다.")
    void rejectInvalidCredentials() {
        // given
        var unknownRequest = AuthRequestFixture.login("unknown@example.com", PASSWORD);
        var wrongPasswordRequest = AuthRequestFixture.login(EMAIL, UserFixture.WRONG_PASSWORD);
        given(users.findByEmail(unknownRequest.email())).willReturn(Optional.empty());
        given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));

        // when
        Throwable unknownError = catchThrowable(() -> service.login(unknownRequest));
        Throwable passwordError = catchThrowable(() -> service.login(wrongPasswordRequest));

        // then
        assertAuthError(unknownError, AuthErrorCode.INVALID_CREDENTIALS);
        assertAuthError(passwordError, AuthErrorCode.INVALID_CREDENTIALS);
        then(refreshTokens).shouldHaveNoInteractions();
        then(jwtTokens).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("비활성 사용자의 로그인을 거절할 수 있다.")
    void rejectDisabledUserLogin() {
        // given
        var request = AuthRequestFixture.login();
        user.disable();
        given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));

        // when
        Throwable error = catchThrowable(() -> service.login(request));

        // then
        assertAuthError(error, AuthErrorCode.INVALID_CREDENTIALS);
        then(refreshTokens).shouldHaveNoInteractions();
        then(jwtTokens).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("72바이트를 넘는 비밀번호를 거절할 수 있다.")
    void rejectLongPassword() {
        // given
        var request = AuthRequestFixture.login(EMAIL, "가".repeat(30));
        given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));

        // when
        Throwable error = catchThrowable(() -> service.login(request));

        // then
        assertAuthError(error, AuthErrorCode.INVALID_CREDENTIALS);
        then(refreshTokens).shouldHaveNoInteractions();
        then(jwtTokens).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("최초 만료 시간을 유지하며 토큰을 갱신할 수 있다.")
    void rotateRefreshToken() {
        // given
        RefreshToken token = RefreshTokenFixture.create(user);
        var refreshTime = NOW.plus(Duration.ofDays(6));
        service = new AuthService(users, refreshTokens, UserFixture.passwordEncoder(), jwtTokens, blacklist,
                JwtFixture.properties(), Clock.fixed(refreshTime, ZoneOffset.UTC), authMapper);
        given(refreshTokens.findForUpdateByTokenHash(TOKEN_HASH)).willReturn(Optional.of(token));
        given(jwtTokens.issueAccessToken(user, token.getSessionId(), refreshTime, refreshTime.plus(JwtFixture.ACCESS_TTL)))
                .willReturn("new-access-token");

        // when
        var response = service.refresh(RAW_TOKEN);

        // then
        assertThat(token.getRevokedAt()).isEqualTo(refreshTime);
        assertThat(response.refreshToken()).isNotEqualTo(RAW_TOKEN);
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.expiresIn()).isEqualTo(1800);
        assertThat(response.refreshExpiresIn()).isEqualTo(86400);
        assertThat(response.user()).isEqualTo(UserFixture.response());
        var stored = ArgumentCaptor.forClass(RefreshToken.class);
        then(refreshTokens).should().save(stored.capture());
        assertThat(stored.getValue().getExpiresAt()).isEqualTo(token.getExpiresAt());
        assertThat(stored.getValue().getSessionId()).isEqualTo(token.getSessionId());
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "1800, 1800", "1801, 1800"})
    @DisplayName("로그인 만료 시간 안에서 접근 토큰을 발급할 수 있다.")
    void capAccessTokenExpiry(long remainingSeconds, long accessSeconds) {
        // given
        var expiresAt = NOW.plusSeconds(remainingSeconds);
        var token = RefreshTokenFixture.create(user, TOKEN_HASH, expiresAt);
        given(refreshTokens.findForUpdateByTokenHash(TOKEN_HASH)).willReturn(Optional.of(token));
        given(jwtTokens.issueAccessToken(user, token.getSessionId(), NOW, NOW.plusSeconds(accessSeconds)))
                .willReturn("new-access-token");

        // when
        var response = service.refresh(RAW_TOKEN);

        // then
        assertThat(response.expiresIn()).isEqualTo(accessSeconds);
        assertThat(response.refreshExpiresIn()).isEqualTo(remainingSeconds);
        then(jwtTokens).should().issueAccessToken(user, token.getSessionId(), NOW, NOW.plusSeconds(accessSeconds));
        var stored = ArgumentCaptor.forClass(RefreshToken.class);
        then(refreshTokens).should().save(stored.capture());
        assertThat(stored.getValue().getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("없는 토큰의 갱신을 거절할 수 있다.")
    void rejectUnknownRefreshToken() {
        // given
        given(refreshTokens.findForUpdateByTokenHash(TOKEN_HASH)).willReturn(Optional.empty());

        // when
        Throwable error = catchThrowable(() -> service.refresh(RAW_TOKEN));

        // then
        assertRejectedRefresh(error);
    }

    @Test
    @DisplayName("차단된 로그인의 토큰 갱신을 거절할 수 있다.")
    void rejectBlockedSession() {
        // given
        var token = RefreshTokenFixture.create(user);
        given(refreshTokens.findForUpdateByTokenHash(TOKEN_HASH)).willReturn(Optional.of(token));
        given(blacklist.isBlocked(token.getSessionId())).willReturn(true);

        // when
        Throwable error = catchThrowable(() -> service.refresh(RAW_TOKEN));

        // then
        assertRejectedRefresh(error);
        assertThat(token.getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("만료된 토큰의 갱신을 거절할 수 있다.")
    void rejectExpiredRefreshToken() {
        // given
        given(refreshTokens.findForUpdateByTokenHash(TOKEN_HASH)).willReturn(Optional.of(RefreshTokenFixture.expired(user)));

        // when
        Throwable error = catchThrowable(() -> service.refresh(RAW_TOKEN));

        // then
        assertRejectedRefresh(error);
    }

    @Test
    @DisplayName("폐기된 토큰의 갱신을 거절할 수 있다.")
    void rejectRevokedRefreshToken() {
        // given
        given(refreshTokens.findForUpdateByTokenHash(TOKEN_HASH)).willReturn(Optional.of(RefreshTokenFixture.revoked(user)));

        // when
        Throwable error = catchThrowable(() -> service.refresh(RAW_TOKEN));

        // then
        assertRejectedRefresh(error);
    }

    @Test
    @DisplayName("비활성 사용자의 토큰 갱신을 거절할 수 있다.")
    void rejectDisabledUserRefresh() {
        // given
        user.disable();
        given(refreshTokens.findForUpdateByTokenHash(TOKEN_HASH)).willReturn(Optional.of(RefreshTokenFixture.create(user)));

        // when
        Throwable error = catchThrowable(() -> service.refresh(RAW_TOKEN));

        // then
        assertRejectedRefresh(error);
    }

    @Test
    @DisplayName("반복 로그아웃을 처리할 수 있다.")
    void logoutRepeatedly() {
        // given
        RefreshToken token = RefreshTokenFixture.create(user);
        given(refreshTokens.findForUpdateByTokenHash(TOKEN_HASH)).willReturn(Optional.of(token));

        // when
        service.logout(RAW_TOKEN);
        service.logout(RAW_TOKEN);

        // then
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
        then(blacklist).should(times(2)).block(token.getSessionId(), token.getExpiresAt());
        then(jwtTokens).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("없는 토큰으로도 로그아웃할 수 있다.")
    void logoutUnknownToken() {
        // given
        given(refreshTokens.findForUpdateByTokenHash(TOKEN_HASH)).willReturn(Optional.empty());

        // when
        service.logout(RAW_TOKEN);

        // then
        then(refreshTokens).should(never()).save(any());
        then(blacklist).shouldHaveNoInteractions();
        then(jwtTokens).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("활성 사용자를 조회할 수 있다.")
    void loadActiveUser() {
        // given
        given(users.findById(UserFixture.ID)).willReturn(Optional.of(user));

        // when
        var result = service.loadActiveUser(UserFixture.ID);

        // then
        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("유효하지 않은 사용자를 거절할 수 있다.")
    void rejectUnavailableUser() {
        // given
        user.disable();
        given(users.findById(1L)).willReturn(Optional.of(user));
        given(users.findById(2L)).willReturn(Optional.empty());

        // when
        Throwable disabledError = catchThrowable(() -> service.loadActiveUser(1L));
        Throwable missingError = catchThrowable(() -> service.loadActiveUser(2L));

        // then
        assertThat(disabledError).isInstanceOf(BadCredentialsException.class);
        assertThat(missingError).isInstanceOf(BadCredentialsException.class);
    }

    private void assertRejectedRefresh(Throwable error) {
        assertAuthError(error, AuthErrorCode.INVALID_REFRESH_TOKEN);
        then(refreshTokens).should(never()).save(any());
        then(jwtTokens).shouldHaveNoInteractions();
    }

    private void assertAuthError(Throwable error, ErrorCode expected) {
        assertThat(error).isInstanceOfSatisfying(RestApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = {AuthMapper.class, UserMapper.class})
    static class MapperConfiguration {
    }
}
