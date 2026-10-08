package com.codeiary.domain.users.service;

import com.codeiary.domain.users.dto.UserMapper;
import com.codeiary.domain.users.dto.request.OnboardingRequest;
import com.codeiary.domain.users.dto.request.UpdateProfileRequest;
import com.codeiary.domain.users.dto.response.UserProfileResponse;
import com.codeiary.domain.users.entity.User;
import com.codeiary.domain.users.entity.enums.Role;
import com.codeiary.domain.users.exception.UserErrorCode;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.domain.users.repository.UserRepository;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.exception.SecurityErrorCode;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository users;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(users, Mappers.getMapper(UserMapper.class));
    }

    @Test
    @DisplayName("닉네임으로 온보딩을 완료하고 USER 권한을 부여할 수 있다.")
    void completeOnboarding() {
        // given
        User user = UserFixture.createWithId(Role.PENDING);
        given(users.findByIdForUpdate(UserFixture.ID)).willReturn(Optional.of(user));
        given(users.saveAndFlush(user)).willReturn(user);

        // when
        UserProfileResponse response = service.completeOnboarding(
                UserFixture.ID, new OnboardingRequest("기록자"));

        // then
        assertThat(response.nickname()).isEqualTo("기록자");
        assertThat(response.onboardingCompleted()).isTrue();
        assertThat(response.profileImageUrl()).isNull();
        assertThat(response.role()).isEqualTo(Role.USER);
        then(users).should().findByIdForUpdate(UserFixture.ID);
        then(users).should().saveAndFlush(user);
    }

    @Test
    @DisplayName("로그인 정보와 권한을 유지하며 공개 프로필을 수정할 수 있다.")
    void updateProfile() {
        // given
        User user = UserFixture.createWithId();
        given(users.findByIdForUpdate(UserFixture.ID)).willReturn(Optional.of(user));
        given(users.saveAndFlush(user)).willReturn(user);
        UpdateProfileRequest request = new UpdateProfileRequest(
                "기록자", "https://img.example.com/photo.jpg", "https://github.com/writer", "contact@example.com");

        // when
        UserProfileResponse response = service.updateProfile(UserFixture.ID, request);

        // then
        assertThat(response.nickname()).isEqualTo(request.nickname());
        assertThat(response.profileImageUrl()).isEqualTo(request.profileImageUrl());
        assertThat(response.githubUrl()).isEqualTo(request.githubUrl());
        assertThat(response.contactEmail()).isEqualTo(request.contactEmail());
        assertThat(response.email()).isEqualTo(UserFixture.EMAIL);
        assertThat(response.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("비워 둔 프로필 사진과 공개 연락처를 삭제할 수 있다.")
    void clearOptionalProfile() {
        // given
        User user = UserFixture.createWithId();
        user.updatePublicProfile("기록자", "https://img.example.com/photo.jpg",
                "https://github.com/writer", "contact@example.com");
        given(users.findByIdForUpdate(UserFixture.ID)).willReturn(Optional.of(user));
        given(users.saveAndFlush(user)).willReturn(user);

        // when
        UserProfileResponse response = service.updateProfile(
                UserFixture.ID, new UpdateProfileRequest("기록자", "", null, ""));

        // then
        assertThat(response.profileImageUrl()).isNull();
        assertThat(response.githubUrl()).isNull();
        assertThat(response.contactEmail()).isNull();
        assertThat(response.email()).isEqualTo(UserFixture.EMAIL);
    }

    @Test
    @DisplayName("다른 사용자가 사용 중인 닉네임을 거절할 수 있다.")
    void rejectTakenNickname() {
        // given
        User user = UserFixture.createWithId(Role.PENDING);
        given(users.findByIdForUpdate(UserFixture.ID)).willReturn(Optional.of(user));
        given(users.existsByNicknameIgnoreCaseAndIdNot("기록자", UserFixture.ID)).willReturn(true);

        // when, then
        assertThatThrownBy(() -> service.completeOnboarding(UserFixture.ID, new OnboardingRequest("기록자")))
                .isInstanceOfSatisfying(RestApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.NICKNAME_TAKEN));
        then(users).should(never()).saveAndFlush(any());
        assertThat(user.getNickname()).isNull();
        assertThat(user.getRole()).isEqualTo(Role.PENDING);
    }

    @Test
    @DisplayName("동시에 저장된 중복 닉네임을 충돌로 처리할 수 있다.")
    void rejectConcurrentNickname() {
        // given
        User user = UserFixture.createWithId();
        given(users.findByIdForUpdate(UserFixture.ID)).willReturn(Optional.of(user));
        given(users.saveAndFlush(user)).willThrow(new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate", new SQLException(), "uk_users_nickname")));

        // when, then
        assertThatThrownBy(() -> service.completeOnboarding(UserFixture.ID, new OnboardingRequest("기록자")))
                .isInstanceOfSatisfying(RestApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.NICKNAME_TAKEN));
    }

    @Test
    @DisplayName("닉네임과 무관한 저장 오류를 구분할 수 있다.")
    void preserveOtherPersistenceErrors() {
        // given
        User user = UserFixture.createWithId();
        DataIntegrityViolationException failure = new DataIntegrityViolationException("other constraint");
        given(users.findByIdForUpdate(UserFixture.ID)).willReturn(Optional.of(user));
        given(users.saveAndFlush(user)).willThrow(failure);

        // when, then
        assertThatThrownBy(() -> service.completeOnboarding(UserFixture.ID, new OnboardingRequest("기록자")))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("내 계정을 제외하고 닉네임 사용 가능 여부를 확인할 수 있다.")
    void checkNicknameAvailability() {
        // given
        given(users.existsByNicknameIgnoreCaseAndIdNot("기록자", UserFixture.ID)).willReturn(false);
        given(users.existsByNicknameIgnoreCaseAndIdNot("다른기록자", UserFixture.ID)).willReturn(true);

        // when, then
        assertThat(service.checkNickname(UserFixture.ID, "기록자").available()).isTrue();
        assertThat(service.checkNickname(UserFixture.ID, "다른기록자").available()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"a", "두 단어", "잘못된!닉네임"})
    @DisplayName("허용되지 않은 닉네임을 거절할 수 있다.")
    void rejectInvalidNickname(String nickname) {
        // when, then
        assertThatThrownBy(() -> service.checkNickname(UserFixture.ID, nickname))
                .isInstanceOfSatisfying(RestApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.INVALID_NICKNAME));
        then(users).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("사용자 ID와 닉네임으로 공개 프로필을 조회할 수 있다.")
    void readPublicProfile() {
        // given
        User user = UserFixture.createWithId();
        user.updatePublicProfile("Writer", null, "https://github.com/writer", "contact@example.com");
        given(users.findById(UserFixture.ID)).willReturn(Optional.of(user));
        given(users.findByNicknameIgnoreCase("writer")).willReturn(Optional.of(user));

        // when, then
        assertThat(service.getPublicProfile(UserFixture.ID))
                .isEqualTo(service.getPublicProfileByNickname("writer"))
                .satisfies(profile -> {
                    assertThat(profile.nickname()).isEqualTo("Writer");
                    assertThat(profile.contactEmail()).isEqualTo("contact@example.com");
                });
    }

    @Test
    @DisplayName("비활성 계정과 온보딩 전 계정의 공개 조회를 차단할 수 있다.")
    void hideUnavailableProfiles() {
        // given
        User disabled = UserFixture.createWithId();
        disabled.updateProfile("기록자", null);
        disabled.disable();
        given(users.findById(UserFixture.ID)).willReturn(Optional.of(disabled));
        given(users.findByNicknameIgnoreCase("미완료")).willReturn(Optional.of(UserFixture.createWithId(Role.PENDING)));

        // when, then
        assertThatThrownBy(() -> service.getPublicProfile(UserFixture.ID))
                .isInstanceOfSatisfying(RestApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.NOT_FOUND_USER));
        assertThatThrownBy(() -> service.getPublicProfileByNickname("미완료"))
                .isInstanceOfSatisfying(RestApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.NOT_FOUND_USER));
    }

    @Test
    @DisplayName("비활성화된 계정의 프로필 변경을 차단할 수 있다.")
    void rejectDisabledUser() {
        // given
        User user = UserFixture.createWithId();
        user.disable();
        given(users.findByIdForUpdate(UserFixture.ID)).willReturn(Optional.of(user));

        // when, then
        assertThatThrownBy(() -> service.updateProfile(UserFixture.ID,
                new UpdateProfileRequest("기록자", null, null, null)))
                .isInstanceOfSatisfying(RestApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(SecurityErrorCode.UNAUTHORIZED));
        then(users).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("기존 관리자 권한과 프로필 이미지를 유지하며 온보딩을 완료할 수 있다.")
    void preserveExistingProfileOnOnboarding() {
        // given
        User user = UserFixture.createWithId(Role.ADMIN);
        user.updateProfile("이전기록자", "https://img.example.com/photo.jpg");
        given(users.findByIdForUpdate(UserFixture.ID)).willReturn(Optional.of(user));
        given(users.saveAndFlush(user)).willReturn(user);

        // when
        UserProfileResponse response = service.completeOnboarding(
                UserFixture.ID, new OnboardingRequest("기록자"));

        // then
        assertThat(response.nickname()).isEqualTo("기록자");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(response.profileImageUrl()).isEqualTo("https://img.example.com/photo.jpg");
        then(users).should().saveAndFlush(user);
    }
}
