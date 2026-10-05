package com.codeiary.global.auth.fixture;

import com.codeiary.global.auth.entity.RefreshToken;
import com.codeiary.domain.users.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

public final class RefreshTokenFixture {

    public static final String RAW_TOKEN = "r".repeat(43);
    // Known SHA-256 digest of RAW_TOKEN; independent of AuthService's implementation.
    public static final String TOKEN_HASH = "d9ef4334ec1e720d52182936117c00ed352c044d0f8d46cd845298cbc1869b72";

    private RefreshTokenFixture() {
    }

    public static RefreshToken create(User user) {
        return create(user, TOKEN_HASH, JwtFixture.NOW.plus(JwtFixture.REFRESH_TTL));
    }

    public static RefreshToken create(User user, String hash, Instant expiresAt) {
        return new RefreshToken(user, hash, JwtFixture.SESSION_ID, expiresAt);
    }

    public static RefreshToken expired(User user) {
        return create(user, TOKEN_HASH, JwtFixture.NOW);
    }

    public static RefreshToken revoked(User user) {
        RefreshToken token = create(user);
        token.revoke(JwtFixture.NOW.minusSeconds(60));
        return token;
    }

    public static String hashOf(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
