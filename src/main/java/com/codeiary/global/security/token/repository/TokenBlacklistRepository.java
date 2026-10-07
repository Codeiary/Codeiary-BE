package com.codeiary.global.security.token.repository;

import com.codeiary.global.security.token.entity.TokenBlacklist;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, UUID> {

    @Modifying
    @Query("delete from TokenBlacklist token where token.expiresAt <= :now")
    void deleteExpired(@Param("now") Instant now);
}
