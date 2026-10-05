package com.codeiary.global.auth.service;

import com.codeiary.global.auth.repository.RefreshTokenRepository;
import com.codeiary.global.auth.repository.TokenBlacklistRepository;
import java.time.Clock;
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
    private final Clock authClock;

    @Scheduled(cron = "${auth.token-cleanup.cron:0 0 3 * * *}", zone = "${auth.token-cleanup.zone:Asia/Seoul}")
    @Transactional
    public void deleteExpiredTokens() {
        Instant now = authClock.instant();
        refreshTokens.deleteExpired(now);
        blacklist.deleteExpired(now);
    }
}
