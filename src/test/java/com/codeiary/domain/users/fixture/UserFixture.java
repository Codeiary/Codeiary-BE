package com.codeiary.domain.users.fixture;

import com.codeiary.domain.users.dto.response.UserProfileResponse;
import com.codeiary.domain.users.entity.User;
import com.codeiary.domain.users.entity.enums.Role;
import org.springframework.test.util.ReflectionTestUtils;

public final class UserFixture {

    public static final long ID = 1L;
    public static final String EMAIL = "admin@example.com";
    public static final String NAME = "김원석";

    private UserFixture() {
    }

    public static User create() {
        return create(EMAIL);
    }

    public static User create(String email) {
        return User.create(email, NAME, Role.ADMIN);
    }

    public static User create(Role role) {
        return User.create(EMAIL, NAME, role);
    }

    public static User createDefaultUser() {
        return User.create(EMAIL, NAME);
    }

    public static User createWithId() {
        return createWithId(Role.ADMIN);
    }

    public static User createWithId(Role role) {
        User user = create(role);
        ReflectionTestUtils.setField(user, "id", ID);
        return user;
    }

    public static UserProfileResponse response() {
        return response(Role.ADMIN);
    }

    public static UserProfileResponse response(Role role) {
        return new UserProfileResponse(ID, EMAIL, NAME, null, null, role != Role.PENDING, role, null, null);
    }
}
