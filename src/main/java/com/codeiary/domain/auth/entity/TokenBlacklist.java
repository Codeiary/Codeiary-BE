package com.codeiary.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "token_blacklist")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenBlacklist {

    @Id
    private UUID sessionId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Builder(access = AccessLevel.PRIVATE)
    private TokenBlacklist(UUID sessionId, Instant expiresAt) {
        this.sessionId = sessionId;
        this.expiresAt = expiresAt;
    }

    public static TokenBlacklist create(UUID sessionId, Instant expiresAt) {
        return TokenBlacklist.builder()
                .sessionId(sessionId)
                .expiresAt(expiresAt)
                .build();
    }
}
