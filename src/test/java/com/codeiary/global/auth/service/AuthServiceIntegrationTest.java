package com.codeiary.global.auth.service;

import com.codeiary.global.auth.fixture.AuthRequestFixture;
import com.codeiary.global.auth.dto.response.TokenResponse;
import com.codeiary.global.auth.exception.AuthErrorCode;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.global.auth.repository.RefreshTokenRepository;
import com.codeiary.domain.users.repository.UserRepository;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.auth.repository.TokenBlacklistRepository;
import com.codeiary.support.IntegrationTestSupport;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class AuthServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired private AuthService service;
    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JwtTokenService jwtTokens;
    @Autowired private TokenBlacklistRepository blacklist;

    @BeforeEach
    void createUser() {
        tokens.deleteAllInBatch();
        blacklist.deleteAllInBatch();
        users.deleteAllInBatch();
        users.saveAndFlush(UserFixture.create(passwords));
    }

    @Test
    @DisplayName("동시 토큰 갱신을 한 번만 처리할 수 있다.")
    void refreshOnceConcurrently() throws Exception {
        // given
        var original = service.login(AuthRequestFixture.login());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var action = (java.util.concurrent.Callable<String>) () -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시 요청 시작 대기 시간이 초과되었습니다.");
                }
                try {
                    service.refresh(original.refreshToken());
                    return "success";
                } catch (RestApiException exception) {
                    return exception.getErrorCode().name();
                }
            };
            var first = executor.submit(action);
            var second = executor.submit(action);
            // when
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            // then
            assertThat(outcomes)
                    .containsExactlyInAnyOrder("success", "INVALID_REFRESH_TOKEN");
        }
        var stored = tokens.findAll();
        assertThat(stored).hasSize(2).filteredOn(token -> token.getRevokedAt() == null).hasSize(1);
        assertThat(stored).extracting(token -> token.getExpiresAt()).containsOnly(stored.getFirst().getExpiresAt());
        assertThat(stored).extracting(token -> token.getSessionId()).containsOnly(stored.getFirst().getSessionId());
    }

    @Test
    @DisplayName("갱신과 로그아웃이 겹쳐도 토큰을 차단할 수 있다.")
    void blockConcurrentRefreshAndLogout() throws Exception {
        // given
        var original = service.login(AuthRequestFixture.login());
        var start = new CountDownLatch(1);
        TokenResponse refreshed;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var refresh = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("요청 시작 대기 시간이 초과되었습니다.");
                }
                try {
                    return service.refresh(original.refreshToken());
                } catch (RestApiException exception) {
                    assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
                    return null;
                }
            });
            var logout = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("요청 시작 대기 시간이 초과되었습니다.");
                }
                service.logout(original.refreshToken());
                return true;
            });

            // when
            start.countDown();
            refreshed = refresh.get(15, TimeUnit.SECONDS);
            logout.get(15, TimeUnit.SECONDS);
        }

        // then
        assertThat(catchThrowable(() -> jwtTokens.validateAccessToken(original.accessToken())))
                .isInstanceOf(JwtException.class);
        if (refreshed != null) {
            assertThat(catchThrowable(() -> jwtTokens.validateAccessToken(refreshed.accessToken())))
                    .isInstanceOf(JwtException.class);
            assertThat(catchThrowable(() -> service.refresh(refreshed.refreshToken())))
                    .isInstanceOfSatisfying(RestApiException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
        }
    }
}
