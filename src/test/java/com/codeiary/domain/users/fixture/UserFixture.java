package com.codeiary.domain.users.fixture;

import com.codeiary.domain.users.dto.response.UserProfileResponse;
import com.codeiary.domain.users.entity.User;
import com.codeiary.domain.users.entity.enums.Role;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

public final class UserFixture {

    public static final long ID = 1L;
    public static final String EMAIL = "admin@example.com";
    public static final String PASSWORD = "Codeiary123!@#";
    public static final String WRONG_PASSWORD = "Wrongpass123!@#";
    public static final String NAME = "김원석";
    private static final String PASSWORD_HASH = passwordEncoder().encode(PASSWORD);

    private UserFixture() {
    }

    public static User create() {
        return create(EMAIL);
    }

    public static User create(String email) {
        return new User(email, PASSWORD_HASH, NAME, Role.ADMIN);
    }

    public static User create(Role role) {
        return new User(EMAIL, PASSWORD_HASH, NAME, role);
    }

    public static User createDefaultUser() {
        return new User(EMAIL, PASSWORD_HASH, NAME);
    }

    public static User create(PasswordEncoder encoder) {
        return new User(EMAIL, encoder.encode(PASSWORD), NAME, Role.ADMIN);
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
        return new UserProfileResponse(ID, EMAIL, NAME, role);
    }

    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(4);
    }
}
