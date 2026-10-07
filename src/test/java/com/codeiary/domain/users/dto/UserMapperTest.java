package com.codeiary.domain.users.dto;

import com.codeiary.domain.users.dto.response.UserProfileResponse;
import com.codeiary.domain.users.entity.User;
import com.codeiary.domain.users.entity.enums.Role;
import com.codeiary.domain.users.fixture.UserFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    @DisplayName("닉네임과 프로필 사진을 사용자 응답에 포함할 수 있다.")
    void mapProfile() {
        // given
        User user = UserFixture.createWithId(Role.USER);
        user.updateProfile("원석", "https://example.com/profile.png");

        // when
        UserProfileResponse response = mapper.toResponse(user);

        // then
        assertThat(response).isEqualTo(new UserProfileResponse(
                UserFixture.ID, UserFixture.EMAIL, UserFixture.NAME,
                "원석", "https://example.com/profile.png", true, Role.USER));
    }

    @Test
    @DisplayName("프로필 사진 없이 닉네임으로 온보딩 완료 여부를 구분할 수 있다.")
    void mapOnboardingStatus() {
        // given
        User user = UserFixture.createWithId();

        // when
        UserProfileResponse before = mapper.toResponse(user);
        user.updateProfile("원석", null);
        UserProfileResponse after = mapper.toResponse(user);

        // then
        assertThat(before.onboardingCompleted()).isFalse();
        assertThat(before.nickname()).isNull();
        assertThat(after.onboardingCompleted()).isTrue();
        assertThat(after.profileImageUrl()).isNull();
    }
}
