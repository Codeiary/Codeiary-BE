package com.codeiary.global.security.service;

import com.codeiary.domain.users.entity.User;
import com.codeiary.domain.users.entity.enums.Role;
import com.codeiary.domain.users.exception.UserErrorCode;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.domain.users.repository.UserRepository;
import com.codeiary.global.exception.ErrorCode;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.dto.AuthInfo;
import com.codeiary.global.security.dto.oauth2user.CustomOAuth2User;
import com.codeiary.global.security.exception.SecurityErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OAuthUserServiceTest {

    @Mock private UserRepository users;
    @InjectMocks private OAuthUserService service;

    private final String subject = "google-subject-123";

    @Test
    @DisplayName("이메일이 변경되어도 Google 식별자로 기존 사용자를 찾을 수 있다.")
    void findByProviderSubject() {
        // given
        User existing = UserFixture.createWithId();
        existing.linkOAuthAccount("google", subject);
        given(users.findByOauthProviderAndOauthSubject("google", subject)).willReturn(Optional.of(existing));
        CustomOAuth2User oauthUser = oauthUser("changed@gmail.com", Boolean.TRUE, null);

        // when
        User result = service.getOrCreate(oauthUser);

        // then
        assertThat(result).isSameAs(existing);
        assertThat(result.getEmail()).isEqualTo(UserFixture.EMAIL);
        then(users).should(never()).findByEmailForUpdate(anyString());
        then(users).should(never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("처음 로그인한 Google 사용자를 온보딩 대기 권한과 소문자 이메일로 가입시킬 수 있다.")
    void registerNewUser() {
        // given
        CustomOAuth2User oauthUser = oauthUser("Owner@External.Test", Boolean.TRUE, null);
        given(users.saveAndFlush(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        User result = service.getOrCreate(oauthUser);

        // then
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        then(users).should().saveAndFlush(saved.capture());
        assertThat(result).isSameAs(saved.getValue());
        assertThat(result.getEmail()).isEqualTo("owner@external.test");
        assertThat(result.getName()).isEqualTo(UserFixture.NAME);
        assertThat(result.getRole()).isEqualTo(Role.PENDING);
        assertThat(result.getOauthProvider()).isEqualTo("google");
        assertThat(result.getOauthSubject()).isEqualTo(subject);
        assertThat(result.getNickname()).isNull();
        assertThat(result.isOnboardingCompleted()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"owner@gmail.com,", "owner@company.com,company.com"})
    @DisplayName("Google이 관리하는 이메일의 기존 계정을 안전하게 연결할 수 있다.")
    void linkAuthoritativeEmail(String email, String hostedDomain) {
        // given
        User existing = UserFixture.create(email);
        existing.updateProfile("기존닉네임", null);
        given(users.findByEmailForUpdate(email)).willReturn(Optional.of(existing));
        given(users.saveAndFlush(existing)).willReturn(existing);

        // when
        User result = service.getOrCreate(oauthUser(email, Boolean.TRUE, hostedDomain));

        // then
        assertThat(result).isSameAs(existing);
        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        assertThat(result.getNickname()).isEqualTo("기존닉네임");
        assertThat(result.getOauthProvider()).isEqualTo("google");
        assertThat(result.getOauthSubject()).isEqualTo(subject);
        then(users).should().saveAndFlush(existing);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("Google이 관리하지 않는 이메일의 기존 계정 연결을 거절할 수 있다.")
    void rejectUntrustedEmailLink(String hostedDomain) {
        // given
        User existing = UserFixture.createWithId();
        given(users.findByEmailForUpdate(UserFixture.EMAIL)).willReturn(Optional.of(existing));

        // when
        Throwable error = catchThrowable(() ->
                service.getOrCreate(oauthUser(UserFixture.EMAIL, Boolean.TRUE, hostedDomain)));

        // then
        assertError(error, UserErrorCode.OAUTH_ACCOUNT_CONFLICT);
        assertThat(existing.getOauthProvider()).isNull();
        then(users).should(never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("다른 Google 계정에 연결된 이메일의 로그인을 거절할 수 있다.")
    void rejectDifferentLinkedSubject() {
        // given
        User existing = UserFixture.create("owner@gmail.com");
        existing.linkOAuthAccount("google", "another-google-subject");
        given(users.findByEmailForUpdate(existing.getEmail())).willReturn(Optional.of(existing));

        // when
        Throwable error = catchThrowable(() ->
                service.getOrCreate(oauthUser(existing.getEmail(), Boolean.TRUE, null)));

        // then
        assertError(error, UserErrorCode.OAUTH_ACCOUNT_CONFLICT);
        assertThat(existing.getOauthSubject()).isEqualTo("another-google-subject");
        then(users).should(never()).saveAndFlush(any(User.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Google 연결 여부와 관계없이 비활성 계정의 로그인을 거절할 수 있다.")
    void rejectDisabledUser(boolean linked) {
        // given
        User existing = UserFixture.createWithId();
        existing.disable();
        if (linked) {
            existing.linkOAuthAccount("google", subject);
            given(users.findByOauthProviderAndOauthSubject("google", subject)).willReturn(Optional.of(existing));
        } else {
            given(users.findByEmailForUpdate(UserFixture.EMAIL)).willReturn(Optional.of(existing));
        }

        // when
        Throwable error = catchThrowable(() ->
                service.getOrCreate(oauthUser(UserFixture.EMAIL, Boolean.TRUE, "example.com")));

        // then
        assertError(error, SecurityErrorCode.UNAUTHORIZED);
        then(users).should(never()).saveAndFlush(any(User.class));
    }

    @ParameterizedTest
    @MethodSource("unverifiedEmailValues")
    @DisplayName("이메일 검증 값이 Boolean true가 아니면 로그인을 거절할 수 있다.")
    void rejectUnverifiedEmail(Object verified) {
        // given
        CustomOAuth2User oauthUser = oauthUser("owner@gmail.com", verified, null);

        // when
        Throwable error = catchThrowable(() -> service.getOrCreate(oauthUser));

        // then
        assertError(error, SecurityErrorCode.UNAUTHORIZED);
        then(users).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @CsvSource({
            "github,google-subject-123,owner@gmail.com,Owner",
            "google,'',owner@gmail.com,Owner",
            "google,google-subject-123,'',Owner",
            "google,google-subject-123,owner@gmail.com,''"
    })
    @DisplayName("지원하지 않거나 필수 정보가 없는 소셜 프로필을 거절할 수 있다.")
    void rejectInvalidProfile(String provider, String subject, String email, String name) {
        // given
        CustomOAuth2User oauthUser = oauthUser(new AuthInfo(provider, subject, email, name), Boolean.TRUE, null);

        // when
        Throwable error = catchThrowable(() -> service.getOrCreate(oauthUser));

        // then
        assertError(error, SecurityErrorCode.UNAUTHORIZED);
        then(users).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("동시에 발생한 가입 충돌을 재조회 없이 반환할 수 있다.")
    void rejectConcurrentRegistration() {
        // given
        CustomOAuth2User oauthUser = oauthUser("owner@gmail.com", Boolean.TRUE, null);
        given(users.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("duplicate OAuth account"));

        // when
        Throwable error = catchThrowable(() -> service.getOrCreate(oauthUser));

        // then
        assertError(error, UserErrorCode.OAUTH_REGISTRATION_CONFLICT);
        then(users).should().findByOauthProviderAndOauthSubject("google", subject);
        then(users).should().findByEmailForUpdate("owner@gmail.com");
        then(users).should().saveAndFlush(any(User.class));
        then(users).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("동시 로그인으로 이미 연결된 동일 계정을 다시 사용할 수 있다.")
    void reuseMatchingAccountAfterEmailLock() {
        // given
        User existing = UserFixture.createWithId();
        existing.linkOAuthAccount("google", subject);
        given(users.findByEmailForUpdate(UserFixture.EMAIL)).willReturn(Optional.of(existing));

        // when
        User result = service.getOrCreate(oauthUser(UserFixture.EMAIL, Boolean.TRUE, null));

        // then
        assertThat(result).isSameAs(existing);
        then(users).should(never()).saveAndFlush(any(User.class));
    }

    private CustomOAuth2User oauthUser(String email, Object verified, Object hostedDomain) {
        return oauthUser(new AuthInfo("google", subject, email, UserFixture.NAME), verified, hostedDomain);
    }

    private CustomOAuth2User oauthUser(AuthInfo profile, Object verified, Object hostedDomain) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", profile.socialSubject());
        attributes.put("email_verified", verified);
        attributes.put("hd", hostedDomain);
        return new CustomOAuth2User(new DefaultOAuth2User(List.of(), attributes, "sub"), profile);
    }

    private static Stream<Object> unverifiedEmailValues() {
        return Stream.of(null, Boolean.FALSE, "true");
    }

    private void assertError(Throwable error, ErrorCode code) {
        assertThat(error).isInstanceOfSatisfying(RestApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
