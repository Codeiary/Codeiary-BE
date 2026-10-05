package com.codeiary.global.auth.repository;

import com.codeiary.global.auth.entity.TokenBlacklist;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO token_blacklist (session_id, expires_at) VALUES (:sessionId, :expiresAt)
            ON CONFLICT (session_id) DO UPDATE
            SET expires_at = GREATEST(token_blacklist.expires_at, EXCLUDED.expires_at)
            """, nativeQuery = true)
    void block(@Param("sessionId") UUID sessionId, @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query("delete from TokenBlacklist entry where entry.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
