package com.codeiary.global.security.token.service;

import com.codeiary.global.security.token.repository.RefreshTokenRepository;
import com.codeiary.global.security.token.repository.TokenBlacklistRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final RefreshTokenRepository refreshTokens;
    private final TokenBlacklistRepository blacklist;

    @Scheduled(cron = "${auth.token-cleanup.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredTokens() {
        Instant now = Instant.now();
        refreshTokens.deleteExpired(now);
        blacklist.deleteExpired(now);
    }
}
