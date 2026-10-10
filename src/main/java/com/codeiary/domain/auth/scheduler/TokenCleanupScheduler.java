package com.codeiary.domain.auth.scheduler;

import com.codeiary.domain.auth.repository.RefreshTokenRepository;
import com.codeiary.domain.auth.repository.TokenBlacklistRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

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
