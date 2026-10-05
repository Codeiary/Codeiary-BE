package com.codeiary.global.auth.service;

import com.codeiary.global.auth.repository.TokenBlacklistRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TokenBlacklistService {

    private final TokenBlacklistRepository blacklist;

    public boolean isBlocked(UUID sessionId) {
        return blacklist.existsById(sessionId);
    }

    @Transactional
    public void block(UUID sessionId, Instant expiresAt) {
        blacklist.block(sessionId, expiresAt);
    }
}
