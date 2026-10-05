package com.codeiary.global.auth.fixture;

import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.global.auth.dto.request.LoginRequest;
import com.codeiary.global.auth.dto.request.RefreshTokenRequest;

public final class AuthRequestFixture {

    private AuthRequestFixture() {
    }

    public static LoginRequest login() {
        return login(UserFixture.EMAIL, UserFixture.PASSWORD);
    }

    public static LoginRequest login(String email, String password) {
        return new LoginRequest(email, password);
    }

    public static RefreshTokenRequest refresh() {
        return refresh(RefreshTokenFixture.RAW_TOKEN);
    }

    public static RefreshTokenRequest refresh(String rawToken) {
        return new RefreshTokenRequest(rawToken);
    }
}
