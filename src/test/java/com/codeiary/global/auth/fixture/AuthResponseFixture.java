package com.codeiary.global.auth.fixture;

import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.global.auth.dto.response.TokenResponse;

public final class AuthResponseFixture {

    public static final String ACCESS_TOKEN = "signed-access-token";

    private AuthResponseFixture() {
    }

    public static TokenResponse tokens() {
        return new TokenResponse(ACCESS_TOKEN, RefreshTokenFixture.RAW_TOKEN, "Bearer",
                JwtFixture.ACCESS_TTL.toSeconds(), JwtFixture.REFRESH_TTL.toSeconds(), UserFixture.response());
    }
}
