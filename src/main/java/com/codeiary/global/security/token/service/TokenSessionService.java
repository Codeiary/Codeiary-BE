package com.codeiary.global.security.token.service;

import com.codeiary.domain.users.entity.User;
import com.codeiary.domain.users.repository.UserRepository;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.token.dto.TokenPair;
import com.codeiary.global.security.token.entity.RefreshToken;
import com.codeiary.global.security.token.entity.TokenBlacklist;
import com.codeiary.global.security.token.exception.TokenErrorCode;
import com.codeiary.global.security.token.provider.JwtTokenProvider;
import com.codeiary.global.security.token.repository.RefreshTokenRepository;
import com.codeiary.global.security.token.repository.TokenBlacklistRepository;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TokenSessionService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final TokenBlacklistRepository blacklist;
    private final JwtTokenProvider tokens;

    @Transactional
    public TokenPair createSession(String email) {
        User user = users.findByEmail(email)
                .filter(User::isEnabled)
                .orElseThrow(() -> new RestApiException(TokenErrorCode.TOKEN_INVALID));
        UUID sessionId = UUID.randomUUID();
        TokenPair pair = tokens.generateTokenPair(user.getId(), user.getRole().name(), sessionId);
        saveRefreshToken(user, sessionId, pair);
        return pair;
    }

    // 재사용된 토큰을 거절하더라도 해당 로그인 차단은 커밋해야 한다.
    @Transactional(noRollbackFor = RestApiException.class)
    public TokenPair reissue(String rawRefreshToken) {
        Claims claims = tokens.validateRefreshToken(rawRefreshToken);
        UUID sessionId = UUID.fromString(claims.get("sid", String.class));
        User user = users.findByIdForUpdate(Long.valueOf(claims.getSubject()))
                .filter(User::isEnabled)
                .orElseThrow(() -> new RestApiException(TokenErrorCode.TOKEN_INVALID));

        requireActiveSession(sessionId);
        RefreshToken stored = refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new RestApiException(TokenErrorCode.TOKEN_INVALID));
        if (!stored.getUserId().equals(user.getId()) || !stored.getSessionId().equals(sessionId)) {
            throw new RestApiException(TokenErrorCode.TOKEN_INVALID);
        }
        if (stored.getRevokedAt() != null) {
            revokeSession(sessionId, stored.getExpiresAt());
            throw new RestApiException(TokenErrorCode.TOKEN_REVOKED);
        }
        if (!stored.getExpiresAt().isAfter(Instant.now())) {
            throw new RestApiException(TokenErrorCode.TOKEN_EXPIRED);
        }

        TokenPair pair = tokens.generateTokenPair(
                user.getId(), user.getRole().name(), sessionId, stored.getExpiresAt());
        stored.revoke(Instant.now());
        saveRefreshToken(user, sessionId, pair);
        return pair;
    }

    @Transactional
    public void logout(String rawRefreshToken, String rawAccessToken) {
        Claims claims = logoutClaims(rawRefreshToken, rawAccessToken);
        if (claims == null) {
            return;
        }
        if (users.findByIdForUpdate(Long.valueOf(claims.getSubject())).isEmpty()) {
            return;
        }
        UUID sessionId = UUID.fromString(claims.get("sid", String.class));
        revokeSession(sessionId, claims.getExpiration().toInstant());
    }

    public User authenticate(String rawAccessToken) {
        Claims claims = tokens.validateAccessToken(rawAccessToken);
        requireActiveSession(UUID.fromString(claims.get("sid", String.class)));
        return users.findById(Long.valueOf(claims.getSubject()))
                .filter(User::isEnabled)
                .orElseThrow(() -> new RestApiException(TokenErrorCode.TOKEN_INVALID));
    }

    private void saveRefreshToken(User user, UUID sessionId, TokenPair pair) {
        refreshTokens.save(new RefreshToken(
                user.getId(), hash(pair.refreshToken()), sessionId, pair.refreshExpiresAt()));
    }

    private void requireActiveSession(UUID sessionId) {
        if (blacklist.existsById(sessionId)) {
            throw new RestApiException(TokenErrorCode.TOKEN_REVOKED);
        }
    }

    private void revokeSession(UUID sessionId, Instant tokenExpiresAt) {
        Instant expiresAt = refreshTokens.findSessionExpiresAt(sessionId)
                .filter(expiry -> expiry.isAfter(tokenExpiresAt))
                .orElse(tokenExpiresAt);
        Instant blacklistExpiresAt = blacklist.findById(sessionId)
                .map(TokenBlacklist::getExpiresAt)
                .filter(expiry -> expiry.isAfter(expiresAt))
                .orElse(expiresAt);
        blacklist.save(new TokenBlacklist(sessionId, blacklistExpiresAt));
        refreshTokens.revokeSession(sessionId, Instant.now());
    }

    private Claims logoutClaims(String refreshToken, String accessToken) {
        try {
            return tokens.validateRefreshToken(refreshToken);
        } catch (RestApiException exception) {
            // Refresh Token이 없거나 만료되어도 유효한 Access Token으로 로그아웃할 수 있다.
            try {
                return tokens.validateAccessToken(accessToken);
            } catch (RestApiException ignored) {
                return null;
            }
        }
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
