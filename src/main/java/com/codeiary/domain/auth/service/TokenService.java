package com.codeiary.domain.auth.service;

import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.logging.LogExecution;
import com.codeiary.domain.auth.dto.TokenPair;
import com.codeiary.domain.auth.exception.TokenErrorCode;
import com.codeiary.domain.auth.provider.JwtTokenProvider;
import com.codeiary.domain.auth.repository.RedisTokenRepository;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final UserRepository users;
    private final RedisTokenRepository tokenRepository;
    private final JwtTokenProvider tokens;

    @LogExecution
    public TokenPair login(String email) {
        User user = users.findByEmail(email)
                .filter(User::isEnabled)
                .orElseThrow(() -> new RestApiException(TokenErrorCode.TOKEN_INVALID));
        UUID loginId = UUID.randomUUID();
        TokenPair pair = tokens.generateTokenPair(user.getId(), user.getRole().name(), loginId);
        saveRefreshToken(user, loginId, pair);
        return pair;
    }

    @LogExecution
    public TokenPair reissue(String rawRefreshToken) {
        Claims claims = tokens.validateRefreshToken(rawRefreshToken);
        UUID loginId = UUID.fromString(claims.get("sid", String.class));
        User user = users.findById(Long.valueOf(claims.getSubject()))
                .filter(User::isEnabled)
                .orElseThrow(() -> new RestApiException(TokenErrorCode.TOKEN_INVALID));

        Instant expiresAt = claims.getExpiration().toInstant();
        TokenPair pair = tokens.generateTokenPair(user.getId(), user.getRole().name(), loginId, expiresAt);
        var rotation = tokenRepository.rotate(user.getId(), loginId, hash(rawRefreshToken),
                hash(pair.refreshToken()), expiresAt);
        if (rotation == RedisTokenRepository.Rotation.REUSED
                || rotation == RedisTokenRepository.Rotation.REVOKED) {
            throw new RestApiException(TokenErrorCode.TOKEN_REVOKED);
        }
        if (rotation == RedisTokenRepository.Rotation.INVALID) {
            throw new RestApiException(TokenErrorCode.TOKEN_INVALID);
        }
        return pair;
    }

    @LogExecution
    public void logout(String rawRefreshToken, String rawAccessToken) {
        logoutClaims(rawRefreshToken, rawAccessToken).ifPresent(claims -> {
            UUID loginId = UUID.fromString(claims.get("sid", String.class));
            Long result = tokenRepository.revoke(loginId, claims.getExpiration().toInstant());
            if (!Long.valueOf(1).equals(result)) {
                throw new RestApiException(CommonErrorCode.INTERNAL_SERVER_ERROR,
                        new IllegalStateException("Redis token revoke returned " + result));
            }
        });
    }

    public User authenticate(String rawAccessToken) {
        Claims claims = tokens.validateAccessToken(rawAccessToken);
        UUID loginId = UUID.fromString(claims.get("sid", String.class));
        boolean blacklisted = tokenRepository.isBlacklisted(loginId)
                .orElseThrow(() -> new RestApiException(CommonErrorCode.INTERNAL_SERVER_ERROR,
                        new IllegalStateException("Redis blacklist lookup returned null")));
        if (blacklisted) {
            throw new RestApiException(TokenErrorCode.TOKEN_REVOKED);
        }
        return users.findById(Long.valueOf(claims.getSubject()))
                .filter(User::isEnabled)
                .orElseThrow(() -> new RestApiException(TokenErrorCode.TOKEN_INVALID));
    }

    private void saveRefreshToken(User user, UUID loginId, TokenPair pair) {
        Long result = tokenRepository.save(user.getId(), loginId, hash(pair.refreshToken()), pair.refreshExpiresAt());
        if (!Long.valueOf(1).equals(result)) {
            throw new RestApiException(CommonErrorCode.INTERNAL_SERVER_ERROR,
                    new IllegalStateException("Redis token save returned " + result));
        }
    }

    private Optional<Claims> logoutClaims(String refreshToken, String accessToken) {
        try {
            return Optional.of(tokens.validateRefreshToken(refreshToken));
        } catch (RestApiException exception) {
            try {
                return Optional.of(tokens.validateAccessToken(accessToken));
            } catch (RestApiException ignored) {
                return Optional.empty();
            }
        }
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new RestApiException(CommonErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }
}
