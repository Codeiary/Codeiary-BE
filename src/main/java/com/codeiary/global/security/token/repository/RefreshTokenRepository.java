package com.codeiary.global.security.token.repository;

import com.codeiary.global.security.token.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("select max(token.expiresAt) from RefreshToken token where token.sessionId = :sessionId")
    Optional<Instant> findSessionExpiresAt(@Param("sessionId") UUID sessionId);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken token set token.revokedAt = :now "
            + "where token.sessionId = :sessionId and token.revokedAt is null")
    void revokeSession(@Param("sessionId") UUID sessionId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken token where token.expiresAt <= :now")
    void deleteExpired(@Param("now") Instant now);
}
