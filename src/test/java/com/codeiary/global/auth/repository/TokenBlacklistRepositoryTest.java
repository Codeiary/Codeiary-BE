package com.codeiary.global.auth.repository;

import com.codeiary.global.auth.fixture.JwtFixture;
import com.codeiary.support.RepositoryTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBlacklistRepositoryTest extends RepositoryTestSupport {

    @Autowired private TokenBlacklistRepository blacklist;

    @Test
    @DisplayName("중복 차단에도 가장 늦은 만료 시간을 유지할 수 있다.")
    void blockRepeatedly() {
        // given
        var sessionId = JwtFixture.SESSION_ID;
        var expiresAt = JwtFixture.NOW.plus(JwtFixture.REFRESH_TTL);

        // when
        blacklist.block(sessionId, expiresAt.minusSeconds(60));
        blacklist.block(sessionId, expiresAt);
        blacklist.block(sessionId, expiresAt.minusSeconds(120));

        // then
        assertThat(blacklist.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getSessionId()).isEqualTo(sessionId);
            assertThat(entry.getExpiresAt()).isEqualTo(expiresAt);
        });
    }
}
