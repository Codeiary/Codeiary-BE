package com.codeiary.global.auth.repository;

import com.codeiary.global.auth.fixture.RefreshTokenFixture;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.domain.users.repository.UserRepository;
import com.codeiary.global.auth.fixture.JwtFixture;
import com.codeiary.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class RefreshTokenRepositoryTest extends RepositoryTestSupport {

    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("토큰과 사용자를 잠금 조회할 수 있다.")
    void findTokenForUpdate() {
        // given
        var user = users.saveAndFlush(UserFixture.create());
        var saved = tokens.saveAndFlush(RefreshTokenFixture.create(user));
        entityManager.clear();

        // when
        var found = tokens.findForUpdateByTokenHash(RefreshTokenFixture.TOKEN_HASH);
        var unknown = tokens.findForUpdateByTokenHash("b".repeat(64));

        // then
        assertThat(found).hasValueSatisfying(token -> {
            assertThat(token.getId()).isEqualTo(saved.getId());
            assertThat(token.getUser().getEmail()).isEqualTo(UserFixture.EMAIL);
            assertThat(token.getExpiresAt()).isEqualTo(JwtFixture.NOW.plus(JwtFixture.REFRESH_TTL));
            assertThat(token.getCreatedAt()).isNotNull();
        });
        assertThat(unknown).isEmpty();
    }

    @Test
    @DisplayName("토큰 폐기 시간을 저장할 수 있다.")
    void saveRevocation() {
        // given
        var user = users.saveAndFlush(UserFixture.create());
        Long id = tokens.saveAndFlush(RefreshTokenFixture.create(user)).getId();

        // when
        tokens.findForUpdateByTokenHash(RefreshTokenFixture.TOKEN_HASH).orElseThrow().revoke(JwtFixture.NOW);
        tokens.flush();
        entityManager.clear();
        var stored = tokens.findById(id);

        // then
        assertThat(stored).hasValueSatisfying(token -> assertThat(token.getRevokedAt()).isEqualTo(JwtFixture.NOW));
    }

    @Test
    @DisplayName("중복 토큰 해시를 거절할 수 있다.")
    void rejectDuplicateTokenHash() {
        // given
        var user = users.saveAndFlush(UserFixture.create());
        tokens.saveAndFlush(RefreshTokenFixture.create(user));
        var duplicate = RefreshTokenFixture.create(user);

        // when
        Throwable error = catchThrowable(() -> tokens.saveAndFlush(duplicate));

        // then
        assertThat(error).isInstanceOf(DataIntegrityViolationException.class);
    }
}
