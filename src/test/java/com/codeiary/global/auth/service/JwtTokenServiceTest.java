package com.codeiary.global.auth.service;

import com.codeiary.domain.users.entity.enums.Role;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.global.auth.fixture.JwtFixture;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.codeiary.global.auth.fixture.JwtFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class JwtTokenServiceTest {

    private SecretKey signingKey;
    private JwtTokenService service;
    private TokenBlacklistService blacklist;

    @BeforeEach
    void setUp() {
        signingKey = JwtFixture.signingKey();
        blacklist = mock(TokenBlacklistService.class);
        service = new JwtTokenService(signingKey, JwtFixture.properties(), JwtFixture.fixedClock(), blacklist);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("권한과 만료 시간을 담은 JWT를 발급할 수 있다.")
    void issueAccessToken(Role role) {
        // given
        var user = UserFixture.createWithId(role);

        // when
        String token = service.issueAccessToken(user, JwtFixture.SESSION_ID, NOW, NOW.plus(JwtFixture.ACCESS_TTL));
        var claims = service.validateAccessToken(token);

        // then
        assertThat(claims.getSubject()).isEqualTo(Long.toString(UserFixture.ID));
        assertThat(claims.getIssuer()).isEqualTo("codeiary");
        assertThat(claims.getAudience()).containsExactly("codeiary-api");
        assertThat(claims.get("roles")).isEqualTo(List.of(role.name()));
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plus(JwtFixture.ACCESS_TTL));
        assertThat(claims.get("sid", String.class)).isEqualTo(JwtFixture.SESSION_ID.toString());
    }

    @Test
    @DisplayName("로그인 만료 시점부터 접근 토큰을 거절할 수 있다.")
    void expireAccessTokenAtSessionDeadline() {
        // given
        var expiresAt = NOW.plusSeconds(60);
        String token = service.issueAccessToken(UserFixture.createWithId(), JwtFixture.SESSION_ID, NOW, expiresAt);
        var expiredService = new JwtTokenService(signingKey, JwtFixture.properties(),
                Clock.fixed(expiresAt, ZoneOffset.UTC), blacklist);

        // when
        var claims = service.validateAccessToken(token);
        Throwable error = catchThrowable(() -> expiredService.validateAccessToken(token));

        // then
        assertThat(claims.getExpiration().toInstant()).isEqualTo(expiresAt);
        assertThat(error).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("블랙리스트의 접근 토큰을 거절할 수 있다.")
    void rejectBlacklistedToken() {
        // given
        String token = JwtFixture.sign(signingKey, JwtFixture.accessClaims());
        given(blacklist.isBlocked(JwtFixture.SESSION_ID)).willReturn(true);

        // when
        Throwable error = catchThrowable(() -> service.validateAccessToken(token));

        // then
        assertThat(error).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("로그인 식별자가 잘못된 토큰을 거절할 수 있다.")
    void rejectInvalidSession() {
        // given
        var tokens = List.of(
                JwtFixture.sign(signingKey, JwtFixture.accessClaims().claim("sid", null)),
                JwtFixture.sign(signingKey, JwtFixture.accessClaims().claim("sid", "invalid")));

        // when
        var errors = validationErrors(tokens);

        // then
        assertJwtErrors(errors);
    }

    @Test
    @DisplayName("잘못된 JWT 서명을 거절할 수 있다.")
    void rejectInvalidSignature() {
        // given
        var tokens = List.of("not-a-jwt", JwtFixture.accessClaims().compact(),
                JwtFixture.sign(JwtFixture.signingKey(), JwtFixture.accessClaims()));

        // when
        var errors = validationErrors(tokens);

        // then
        assertJwtErrors(errors);
    }

    @Test
    @DisplayName("JWT의 유효 시간을 검증할 수 있다.")
    void validateTokenLifetime() {
        // given
        var tokens = List.of(
                JwtFixture.sign(signingKey, JwtFixture.accessClaims().issuedAt(Date.from(NOW.minusSeconds(120)))
                        .notBefore(Date.from(NOW.minusSeconds(120))).expiration(Date.from(NOW.minusSeconds(60)))),
                JwtFixture.sign(signingKey, JwtFixture.accessClaims().notBefore(Date.from(NOW.plusSeconds(600)))),
                JwtFixture.sign(signingKey, JwtFixture.accessClaims().expiration(Date.from(NOW))));

        // when
        var errors = validationErrors(tokens);

        // then
        assertJwtErrors(errors);
    }

    @Test
    @DisplayName("JWT 발급 정보와 용도를 검증할 수 있다.")
    void validateTokenClaims() {
        // given
        var tokens = List.of(JwtFixture.sign(signingKey, JwtFixture.accessClaims().issuer("other-issuer")),
                JwtFixture.sign(signingKey, JwtFixture.accessClaims().audience().clear().add("other-api").and()),
                JwtFixture.sign(signingKey, JwtFixture.accessClaims().claim("token_use", "refresh")));

        // when
        var errors = validationErrors(tokens);

        // then
        assertJwtErrors(errors);
    }

    @Test
    @DisplayName("JWT 만료 시간과 식별자를 검증할 수 있다.")
    void validateRequiredClaims() {
        // given
        var tokens = List.of(JwtFixture.sign(signingKey, JwtFixture.accessClaims().expiration(null)),
                JwtFixture.sign(signingKey, JwtFixture.accessClaims().subject("not-an-id")),
                JwtFixture.sign(signingKey, JwtFixture.accessClaims().subject("0")));

        // when
        var errors = validationErrors(tokens);

        // then
        assertJwtErrors(errors);
    }

    private List<Throwable> validationErrors(List<String> tokens) {
        return tokens.stream().map(token -> catchThrowable(() -> service.validateAccessToken(token))).toList();
    }

    private void assertJwtErrors(List<Throwable> errors) {
        assertThat(errors).allSatisfy(error -> assertThat(error).isInstanceOf(JwtException.class));
    }
}
