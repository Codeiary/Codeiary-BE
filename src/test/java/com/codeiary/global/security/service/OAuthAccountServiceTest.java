package com.codeiary.global.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.codeiary.domain.auth.dto.TokenPair;
import com.codeiary.domain.auth.service.TokenService;
import com.codeiary.domain.github.service.GitHubConnectionService;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.entity.enums.OAuthProvider;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.exception.UserErrorCode;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.global.exception.ErrorCode;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.dto.AuthInfo;
import com.codeiary.global.security.dto.oauth2user.CustomOAuth2User;
import com.codeiary.global.security.exception.SecurityErrorCode;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

@ExtendWith(MockitoExtension.class)
class OAuthAccountServiceTest {

  @Mock private UserRepository users;
  @Mock private TokenService tokenService;
  @Mock private GitHubConnectionService githubConnections;
  @Mock private com.codeiary.domain.github.service.GitHubRepositoryService githubRepositories;
  @Mock private org.springframework.transaction.PlatformTransactionManager transactions;
  @InjectMocks private OAuthAccountService service;

  private final String subject = "google-subject-123";
  private final TokenPair pair =
      new TokenPair(
          "access", "refresh", Instant.now().plusSeconds(60), Instant.now().plusSeconds(120));

  @Test
  @DisplayName("이메일이 변경되어도 제공자와 고유 ID로 기존 사용자를 찾을 수 있다.")
  void findByProviderSubject() {
    // given
    User user = UserFixture.createWithId();
    user.linkOAuthAccount(OAuthProvider.GOOGLE, subject);
    given(users.findByOauthProviderAndOauthSubject(OAuthProvider.GOOGLE, subject))
        .willReturn(Optional.of(user));
    AuthInfo profile = authInfo("changed@gmail.com", true, true);
    given(tokenService.login(UserFixture.EMAIL)).willReturn(pair);

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
    given(tokenService.login("owner@external.test")).willReturn(pair);

    // when
    assertThat(complete(profile)).contains(pair);

    // then
    ArgumentCaptor<User> argumentCaptor = ArgumentCaptor.forClass(User.class);
    then(users).should().saveAndFlush(argumentCaptor.capture());
    User user = argumentCaptor.getValue();
    assertThat(user.getEmail()).isEqualTo("owner@external.test");
    assertThat(user.getName()).isEqualTo(UserFixture.NAME);
    assertThat(user.getRole()).isEqualTo(Role.PENDING);
    assertThat(user.getOauthProvider()).isEqualTo(OAuthProvider.GOOGLE);
    assertThat(user.getOauthSubject()).isEqualTo(subject);
    assertThat(user.getNickname()).isNull();
    assertThat(user.isOnboardingCompleted()).isFalse();
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
    given(tokenService.login(email)).willReturn(pair);

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
    User user = UserFixture.createWithId();
    given(users.findByEmailForUpdate(UserFixture.EMAIL)).willReturn(Optional.of(user));

    // when
    Throwable error = catchThrowable(() -> complete(authInfo(UserFixture.EMAIL, true, false)));

    // then
    assertError(error, UserErrorCode.OAUTH_ACCOUNT_CONFLICT);
    assertThat(user.getOauthProvider()).isNull();
    then(users).should(never()).saveAndFlush(any(User.class));
  }

  @Test
  @DisplayName("다른 소셜 계정에 연결된 이메일의 로그인을 거절할 수 있다.")
  void rejectDifferentLinkedSubject() {
    // given
    User user = UserFixture.create("owner@gmail.com");
    user.linkOAuthAccount(OAuthProvider.GOOGLE, "another-google-subject");
    given(users.findByEmailForUpdate(user.getEmail())).willReturn(Optional.of(user));

    // when
    Throwable error = catchThrowable(() -> complete(authInfo(user.getEmail(), true, true)));

    // then
    assertError(error, UserErrorCode.OAUTH_ACCOUNT_CONFLICT);
    assertThat(user.getOauthSubject()).isEqualTo("another-google-subject");
    then(users).should(never()).saveAndFlush(any(User.class));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @DisplayName("계정 연결 여부와 관계없이 비활성 계정의 로그인을 거절할 수 있다.")
  void rejectDisabledUser(boolean linked) {
    // given
    User user = UserFixture.createWithId();
    user.disable();
    if (linked) {
      user.linkOAuthAccount(OAuthProvider.GOOGLE, subject);
      given(users.findByOauthProviderAndOauthSubject(OAuthProvider.GOOGLE, subject))
          .willReturn(Optional.of(user));
    } else {
      given(users.findByEmailForUpdate(UserFixture.EMAIL)).willReturn(Optional.of(user));
    }

    // when
    Throwable error = catchThrowable(() -> complete(authInfo(UserFixture.EMAIL, true, true)));

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
  void rejectInvalidProfile(AuthInfo authInfo) {
    // when
    Throwable error = catchThrowable(() -> complete(authInfo));

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
        .willThrow(
            new DataIntegrityViolationException(
                "duplicate OAuth account",
                new ConstraintViolationException(
                    "duplicate", new SQLException("duplicate", "23505"), constraint)));

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
    var failure =
        new DataIntegrityViolationException(
            "unrelated integrity violation",
            constraint == null
                ? null
                : new ConstraintViolationException(
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
    User user = UserFixture.createWithId();
    user.linkOAuthAccount(OAuthProvider.GOOGLE, subject);
    given(users.findByEmailForUpdate(UserFixture.EMAIL)).willReturn(Optional.of(user));
    given(tokenService.login(UserFixture.EMAIL)).willReturn(pair);

    // when & then
    assertThat(complete(authInfo(UserFixture.EMAIL, true, true))).contains(pair);
    then(users).should(never()).saveAndFlush(any(User.class));
  }

  private Optional<TokenPair> complete(AuthInfo authInfo) {
    var delegate = new DefaultOAuth2User(List.of(), Map.of("sub", subject), "sub");
    var principal = new CustomOAuth2User(delegate, authInfo);
    return service.complete(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()), null);
  }

  private AuthInfo authInfo(String email, boolean verified, boolean authoritative) {
    return new AuthInfo(
        OAuthProvider.GOOGLE, subject, email, UserFixture.NAME, verified, authoritative);
  }

  private static Stream<AuthInfo> invalidProfiles() {
    return Stream.of(
        new AuthInfo(null, "subject", "owner@gmail.com", "Owner", true, true),
        new AuthInfo(OAuthProvider.GOOGLE, null, "owner@gmail.com", "Owner", true, true),
        new AuthInfo(OAuthProvider.GOOGLE, "subject", null, "Owner", true, true));
  }

  private void assertError(Throwable error, ErrorCode errorCode) {
    assertThat(error)
        .isInstanceOfSatisfying(
            RestApiException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
  }
}
