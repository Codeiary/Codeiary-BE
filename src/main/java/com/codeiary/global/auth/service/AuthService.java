package com.codeiary.global.auth.service;

import com.codeiary.global.auth.dto.request.LoginRequest;
import com.codeiary.global.auth.dto.AuthMapper;
import com.codeiary.global.auth.dto.response.TokenResponse;
import com.codeiary.global.auth.entity.RefreshToken;
import com.codeiary.domain.users.entity.User;
import com.codeiary.global.auth.exception.AuthErrorCode;
import com.codeiary.global.auth.repository.RefreshTokenRepository;
import com.codeiary.domain.users.repository.UserRepository;
import com.codeiary.global.auth.config.JwtProperties;
import com.codeiary.global.exception.RestApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokens;
    private final TokenBlacklistService blacklist;
    private final JwtProperties properties;
    private final Clock authClock;
    private final AuthMapper authMapper;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email())
                .orElseThrow(() -> new RestApiException(AuthErrorCode.INVALID_CREDENTIALS));
        if (!user.isEnabled() || request.password().getBytes(StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new RestApiException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        Instant now = authClock.instant().truncatedTo(ChronoUnit.SECONDS);
        return issueTokens(user, UUID.randomUUID(), now, now.plus(properties.refreshTokenTtl()));
    }

    @Transactional
    public TokenResponse refresh(String rawToken) {
        RefreshToken token = refreshTokens.findForUpdateByTokenHash(hash(rawToken))
                .orElseThrow(() -> new RestApiException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        Instant now = authClock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = token.getExpiresAt().truncatedTo(ChronoUnit.SECONDS);
        if (!token.isUsableAt(now) || !expiresAt.isAfter(now) || blacklist.isBlocked(token.getSessionId())) {
            throw new RestApiException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        token.revoke(now);
        return issueTokens(token.getUser(), token.getSessionId(), now, expiresAt);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findForUpdateByTokenHash(hash(rawToken))
                .ifPresent(token -> {
                    Instant now = authClock.instant();
                    token.revoke(now);
                    if (token.getExpiresAt().isAfter(now)) {
                        blacklist.block(token.getSessionId(), token.getExpiresAt());
                    }
                });
    }

    public User loadActiveUser(Long userId) {
        return users.findById(userId)
                .filter(User::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Unavailable user"));
    }

    private TokenResponse issueTokens(User user, UUID sessionId, Instant now, Instant refreshExpiresAt) {
        Instant accessExpiresAt = now.plus(properties.accessTokenTtl());
        if (accessExpiresAt.isAfter(refreshExpiresAt)) {
            accessExpiresAt = refreshExpiresAt;
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user, hash(rawToken), sessionId, refreshExpiresAt));
        return authMapper.toTokenResponse(user,
                jwtTokens.issueAccessToken(user, sessionId, now, accessExpiresAt), rawToken,
                Duration.between(now, accessExpiresAt).toSeconds(),
                Duration.between(now, refreshExpiresAt).toSeconds());
    }

    private static String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

}
