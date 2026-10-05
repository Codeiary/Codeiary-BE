package com.codeiary.global.auth.service;

import com.codeiary.global.auth.fixture.RefreshTokenFixture;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.global.auth.repository.RefreshTokenRepository;
import com.codeiary.domain.users.repository.UserRepository;
import com.codeiary.global.auth.fixture.JwtFixture;
import com.codeiary.global.auth.repository.TokenBlacklistRepository;
import com.codeiary.support.RepositoryTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.codeiary.global.auth.fixture.JwtFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;

class TokenCleanupServiceTest extends RepositoryTestSupport {

    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private TokenBlacklistRepository blacklist;

    @Test
    @DisplayName("만료된 기록만 정리하고 유효한 차단을 유지할 수 있다.")
    void deleteOnlyExpiredTokens() {
        // given
        var user = users.saveAndFlush(UserFixture.create());
        var expired = RefreshTokenFixture.create(user, "a".repeat(64), NOW.minusSeconds(1));
        var boundary = RefreshTokenFixture.create(user, "b".repeat(64), NOW);
        var revoked = RefreshTokenFixture.revoked(user);
        tokens.saveAllAndFlush(List.of(expired, boundary, revoked));
        blacklist.block(UUID.randomUUID(), NOW.minusSeconds(1));
        blacklist.block(UUID.randomUUID(), NOW);
        blacklist.block(revoked.getSessionId(), revoked.getExpiresAt());
        var service = new TokenCleanupService(tokens, blacklist, JwtFixture.fixedClock());

        // when
        service.deleteExpiredTokens();

        // then
        assertThat(tokens.findAll()).singleElement().satisfies(token -> {
            assertThat(token.getId()).isEqualTo(revoked.getId());
            assertThat(token.getRevokedAt()).isNotNull();
        });
        assertThat(blacklist.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getSessionId()).isEqualTo(revoked.getSessionId());
            assertThat(entry.getExpiresAt()).isEqualTo(revoked.getExpiresAt());
        });
    }
}
