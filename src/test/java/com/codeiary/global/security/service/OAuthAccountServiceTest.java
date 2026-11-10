package com.codeiary.global.security.service;

import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.entity.enums.OAuthProvider;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.exception.UserErrorCode;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.global.exception.ErrorCode;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.domain.auth.dto.AuthInfo;
import com.codeiary.domain.auth.dto.TokenPair;
import com.codeiary.domain.auth.service.TokenSessionService;
import com.codeiary.domain.github.service.GitHubConnectionService;
import com.codeiary.global.security.dto.oauth2user.CustomOAuth2User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import com.codeiary.global.security.exception.SecurityErrorCode;
import java.sql.SQLException;
import java.util.Optional;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OAuthAccountServiceTest {

    @Mock private UserRepository users;
    @Mock private TokenSessionService sessions;
    @Mock private GitHubConnectionService githubConnections;
    @InjectMocks private OAuthAccountService service;

    private final String subject = "google-subject-123";
    private final TokenPair pair = new TokenPair("access", "refresh", Instant.now().plusSeconds(60),
            Instant.now().plusSeconds(120));

    @Test
    @DisplayName("이메일이 변경되어도 제공자와 고유 ID로 기존 사용자를 찾을 수 있다.")
    void findByProviderSubject() {
        // given
        User existing = UserFixture.createWithId();
        existing.linkOAuthAccount(OAuthProvider.GOOGLE, subject);
        given(users.findByOauthProviderAndOauthSubject(OAuthProvider.GOOGLE, subject)).willReturn(Optional.of(existing));
        AuthInfo profile = authInfo("changed@gmail.com", true, true);
        given(sessions.createSession(UserFixture.EMAIL)).willReturn(pair);

        // when & then
        assertThat(complete(profile)).contains(pair);
        then(users).should(never()).findByEmailForUpdate(anyString());
        then(users).should(never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("처음 로그인한 사용자를 온보딩 대기 권한과 소문자 이메일로 가입시킬 수 있다.")
    void registerNewUser() {
        // given
        AuthInfo profile = authInfo("Owner@External.Test", true, false);
        given(users.saveAndFlush(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(sessions.createSession("owner@external.test")).willReturn(pair);

        // when
        assertThat(complete(profile)).contains(pair);

        // then
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        then(users).should().saveAndFlush(saved.capture());
        User result = saved.getValue();
        assertThat(result.getEmail()).isEqualTo("owner@external.test");
        assertThat(result.getName()).isEqualTo(UserFixture.NAME);
        assertThat(result.getRole()).isEqualTo(Role.PENDING);
        assertThat(result.getOauthProvider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(result.getOauthSubject()).isEqualTo(subject);
        assertThat(result.getNickname()).isNull();
        assertThat(result.isOnboardingCompleted()).isFalse();
    }

    @Test
    @DisplayName("제공자가 소유권을 보증하는 이메일의 기존 계정을 연결할 수 있다.")
    void linkAuthoritativeEmail() {
        // given
        String email = UserFixture.EMAIL;
        User existing = UserFixture.create(email);
        existing.updateProfile("기존닉네임", null);
        given(users.findByEmailForUpdate(email)).willReturn(Optional.of(existing));
        given(users.saveAndFlush(existing)).willReturn(existing);
        given(sessions.createSession(email)).willReturn(pair);

        // when & then
        assertThat(complete(authInfo(email, true, true))).contains(pair);
        User result = existing;
        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        assertThat(result.getNickname()).isEqualTo("기존닉네임");
        assertThat(result.getOauthProvider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(result.getOauthSubject()).isEqualTo(subject);
        then(users).should().saveAndFlush(existing);
    }

    @Test
    @DisplayName("제공자가 소유권을 보증하지 않는 이메일의 기존 계정 연결을 거절할 수 있다.")
    void rejectUntrustedEmailLink() {
        // given
        User existing = UserFixture.createWithId();
        given(users.findByEmailForUpdate(UserFixture.EMAIL)).willReturn(Optional.of(existing));

        // when
        Throwable error = catchThrowable(() ->
                complete(authInfo(UserFixture.EMAIL, true, false)));

        // then
        assertError(error, UserErrorCode.OAUTH_ACCOUNT_CONFLICT);
        assertThat(existing.getOauthProvider()).isNull();
        then(users).should(never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("다른 소셜 계정에 연결된 이메일의 로그인을 거절할 수 있다.")
    void rejectDifferentLinkedSubject() {
        // given
        User existing = UserFixture.create("owner@gmail.com");
        existing.linkOAuthAccount(OAuthProvider.GOOGLE, "another-google-subject");
        given(users.findByEmailForUpdate(existing.getEmail())).willReturn(Optional.of(existing));

        // when
        Throwable error = catchThrowable(() ->
                complete(authInfo(existing.getEmail(), true, true)));

        // then
        assertError(error, UserErrorCode.OAUTH_ACCOUNT_CONFLICT);
        assertThat(existing.getOauthSubject()).isEqualTo("another-google-subject");
        then(users).should(never()).saveAndFlush(any(User.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("계정 연결 여부와 관계없이 비활성 계정의 로그인을 거절할 수 있다.")
    void rejectDisabledUser(boolean linked) {
        // given
        User existing = UserFixture.createWithId();
        existing.disable();
        if (linked) {
            existing.linkOAuthAccount(OAuthProvider.GOOGLE, subject);
            given(users.findByOauthProviderAndOauthSubject(OAuthProvider.GOOGLE, subject)).willReturn(Optional.of(existing));
        } else {
            given(users.findByEmailForUpdate(UserFixture.EMAIL)).willReturn(Optional.of(existing));
        }

        // when
        Throwable error = catchThrowable(() ->
                complete(authInfo(UserFixture.EMAIL, true, true)));

        // then
        assertError(error, SecurityErrorCode.UNAUTHORIZED);
        then(users).should(never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("인증되지 않은 이메일의 로그인을 거절할 수 있다.")
    void rejectUnverifiedEmail() {
        // given
        AuthInfo profile = authInfo("owner@gmail.com", false, false);

        // when
        Throwable error = catchThrowable(() -> complete(profile));

        // then
        assertError(error, SecurityErrorCode.UNAUTHORIZED);
        then(users).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @MethodSource("invalidProfiles")
    @DisplayName("필수 식별 정보가 없는 소셜 프로필을 거절할 수 있다.")
    void rejectInvalidProfile(AuthInfo profile) {
        // when
        Throwable error = catchThrowable(() -> complete(profile));

        // then
        assertError(error, SecurityErrorCode.UNAUTHORIZED);
        then(users).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"users_email_key", "uk_users_oauth_identity"})
    @DisplayName("이메일과 OAuth 식별자 중복을 가입 충돌로 반환할 수 있다.")
    void rejectConcurrentRegistration(String constraint) {
        // given
        AuthInfo profile = authInfo("owner@gmail.com", true, true);
        given(users.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("duplicate OAuth account",
                        new ConstraintViolationException("duplicate", new SQLException("duplicate", "23505"), constraint)));

        // when
        Throwable error = catchThrowable(() -> complete(profile));

        // then
        assertError(error, UserErrorCode.OAUTH_REGISTRATION_CONFLICT);
        then(users).should().findByOauthProviderAndOauthSubject(OAuthProvider.GOOGLE, subject);
        then(users).should().findByEmailForUpdate("owner@gmail.com");
        then(users).should().saveAndFlush(any(User.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "uk_users_nickname")
    @DisplayName("가입 중복과 관계없는 DB 오류를 그대로 전달할 수 있다.")
    void preserveUnrelatedDatabaseFailure(String constraint) {
        // given
        var failure = new DataIntegrityViolationException("unrelated integrity violation",
                constraint == null ? null : new ConstraintViolationException(
                        "unrelated constraint", new SQLException(), constraint));
        given(users.saveAndFlush(any(User.class))).willThrow(failure);

        // when
        Throwable error = catchThrowable(() -> complete(authInfo("owner@gmail.com", true, true)));

        // then
        assertThat(error).isSameAs(failure);
    }

    @Test
    @DisplayName("동시 로그인으로 이미 연결된 동일 계정을 다시 사용할 수 있다.")
    void reuseMatchingAccountAfterEmailLock() {
        // given
        User existing = UserFixture.createWithId();
        existing.linkOAuthAccount(OAuthProvider.GOOGLE, subject);
        given(users.findByEmailForUpdate(UserFixture.EMAIL)).willReturn(Optional.of(existing));
        given(sessions.createSession(UserFixture.EMAIL)).willReturn(pair);

        // when & then
        assertThat(complete(authInfo(UserFixture.EMAIL, true, true))).contains(pair);
        then(users).should(never()).saveAndFlush(any(User.class));
    }

    private Optional<TokenPair> complete(AuthInfo profile) {
        var delegate = new DefaultOAuth2User(List.of(), Map.of("sub", subject), "sub");
        var principal = new CustomOAuth2User(delegate, profile);
        return service.complete(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()), null);
    }

    private AuthInfo authInfo(String email, boolean verified, boolean authoritative) {
        return new AuthInfo(OAuthProvider.GOOGLE, subject, email, UserFixture.NAME, verified, authoritative);
    }

    private static Stream<AuthInfo> invalidProfiles() {
        return Stream.of(
                new AuthInfo(null, "subject", "owner@gmail.com", "Owner", true, true),
                new AuthInfo(OAuthProvider.GOOGLE, null, "owner@gmail.com", "Owner", true, true),
                new AuthInfo(OAuthProvider.GOOGLE, "subject", null, "Owner", true, true));
    }

    private void assertError(Throwable error, ErrorCode code) {
        assertThat(error).isInstanceOfSatisfying(RestApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
