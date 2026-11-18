package com.codeiary.domain.auth.service;

import com.codeiary.domain.auth.dto.TokenPair;
import com.codeiary.domain.auth.exception.TokenErrorCode;
import com.codeiary.domain.auth.provider.JwtTokenProvider;
import com.codeiary.domain.auth.repository.RedisTokenRepository;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.logging.LogExecution;
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

  private final UserRepository userRepository;
  private final RedisTokenRepository redisTokenRepository;
  private final JwtTokenProvider jwtTokenProvider;

  @LogExecution
  public TokenPair login(String email) {
    User user =
        userRepository
            .findByEmail(email)
            .filter(User::isEnabled)
            .orElseThrow(() -> new RestApiException(TokenErrorCode.TOKEN_INVALID));
    UUID loginId = UUID.randomUUID();
    TokenPair tokenPair =
        jwtTokenProvider.generateTokenPair(user.getId(), user.getRole().name(), loginId);
    saveRefreshToken(user, loginId, tokenPair);
    return tokenPair;
  }

  @LogExecution
  public TokenPair reissue(String rawRefreshToken) {
    Claims claims = jwtTokenProvider.validateRefreshToken(rawRefreshToken);
    UUID loginId = UUID.fromString(claims.get("sid", String.class));
    User user =
        userRepository
            .findById(Long.valueOf(claims.getSubject()))
            .filter(User::isEnabled)
            .orElseThrow(() -> new RestApiException(TokenErrorCode.TOKEN_INVALID));

    Instant expiresAt = claims.getExpiration().toInstant();
    TokenPair tokenPair =
        jwtTokenProvider.generateTokenPair(user.getId(), user.getRole().name(), loginId, expiresAt);
    var rotation =
        redisTokenRepository.rotate(
            user.getId(),
            loginId,
            hash(rawRefreshToken),
            hash(tokenPair.refreshToken()),
            expiresAt);
    if (rotation == RedisTokenRepository.Rotation.REUSED
        || rotation == RedisTokenRepository.Rotation.REVOKED) {
      throw new RestApiException(TokenErrorCode.TOKEN_REVOKED);
    }
    if (rotation == RedisTokenRepository.Rotation.INVALID) {
      throw new RestApiException(TokenErrorCode.TOKEN_INVALID);
    }
    return tokenPair;
  }

  @LogExecution
  public void logout(String rawRefreshToken, String rawAccessToken) {
    logoutClaims(rawRefreshToken, rawAccessToken)
        .ifPresent(claims -> {
          UUID loginId = UUID.fromString(claims.get("sid", String.class));
          Long result = redisTokenRepository.revoke(loginId, claims.getExpiration().toInstant());
          if (!Long.valueOf(1).equals(result)) {
            throw new RestApiException(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                new IllegalStateException("Redis token revoke returned " + result));
          }
        });
  }

  public User authenticate(String rawAccessToken) {
    Claims claims = jwtTokenProvider.validateAccessToken(rawAccessToken);
    UUID loginId = UUID.fromString(claims.get("sid", String.class));
    boolean blacklisted =
        redisTokenRepository
            .isBlacklisted(loginId)
            .orElseThrow(() ->
                new RestApiException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    new IllegalStateException("Redis blacklist lookup returned null")));
    if (blacklisted) {
      throw new RestApiException(TokenErrorCode.TOKEN_REVOKED);
    }
    return userRepository
        .findById(Long.valueOf(claims.getSubject()))
        .filter(User::isEnabled)
        .orElseThrow(() -> new RestApiException(TokenErrorCode.TOKEN_INVALID));
  }

  private void saveRefreshToken(User user, UUID loginId, TokenPair tokenPair) {
    Long result =
        redisTokenRepository.save(
            user.getId(), loginId, hash(tokenPair.refreshToken()), tokenPair.refreshExpiresAt());
    if (!Long.valueOf(1).equals(result)) {
      throw new RestApiException(
          CommonErrorCode.INTERNAL_SERVER_ERROR,
          new IllegalStateException("Redis token save returned " + result));
    }
  }

  private Optional<Claims> logoutClaims(String refreshToken, String accessToken) {
    try {
      return Optional.of(jwtTokenProvider.validateRefreshToken(refreshToken));
    } catch (RestApiException exception) {
      try {
        return Optional.of(jwtTokenProvider.validateAccessToken(accessToken));
      } catch (RestApiException ignored) {
        return Optional.empty();
      }
    }
  }

  private String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new RestApiException(CommonErrorCode.INTERNAL_SERVER_ERROR, exception);
    }
  }
}
