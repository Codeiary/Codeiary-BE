package com.codeiary.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeiary.support.IntegrationTestSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

class RedisTokenRepositoryTest extends IntegrationTestSupport {

  @Autowired private RedisTokenRepository tokenRepository;
  @Autowired private RedisTemplate<String, String> redis;

  @Test
  @DisplayName("리프레시 토큰 재사용 시 Redis에서 원자적으로 블랙리스트에 등록할 수 있다.")
  void rotateAndRevokeOnReuse() {
    UUID loginId = UUID.randomUUID();
    Instant expiresAt = Instant.now().plusSeconds(120).truncatedTo(ChronoUnit.SECONDS);
    try {
      assertThat(tokenRepository.save(1L, loginId, "first-hash", expiresAt)).isEqualTo(1L);
      assertThat(tokenRepository.save(1L, loginId, "another-hash", expiresAt)).isZero();

      assertThat(tokenRepository.isBlacklisted(loginId)).contains(false);
      assertThat(redis.getExpire(refreshKey(loginId))).isBetween(1L, 120L);
      assertThat(tokenRepository.rotate(1L, loginId, "first-hash", "second-hash", expiresAt))
          .isEqualTo(RedisTokenRepository.Rotation.ROTATED);
      assertThat(tokenRepository.rotate(1L, loginId, "first-hash", "another-hash", expiresAt))
          .isEqualTo(RedisTokenRepository.Rotation.REUSED);
      assertThat(tokenRepository.isBlacklisted(loginId)).contains(true);
      assertThat(redis.hasKey(refreshKey(loginId))).isFalse();
      assertThat(redis.getExpire(revokedKey(loginId))).isBetween(1L, 120L);
      assertThat(tokenRepository.rotate(1L, loginId, "second-hash", "third-hash", expiresAt))
          .isEqualTo(RedisTokenRepository.Rotation.REVOKED);
    } finally {
      deleteKeys(loginId);
    }
  }

  @Test
  @DisplayName("로그아웃 시 리프레시 토큰을 삭제하고 블랙리스트 만료 시각을 유지할 수 있다.")
  void revokeWithAccessTokenExpiry() {
    UUID loginId = UUID.randomUUID();
    Instant refreshExpiry = Instant.now().plusSeconds(120).truncatedTo(ChronoUnit.SECONDS);
    Instant accessExpiry = Instant.now().plusSeconds(30).truncatedTo(ChronoUnit.SECONDS);
    try {
      tokenRepository.save(1L, loginId, "hash", refreshExpiry);

      assertThat(tokenRepository.revoke(loginId, accessExpiry)).isEqualTo(1L);

      assertThat(redis.hasKey(refreshKey(loginId))).isFalse();
      assertThat(tokenRepository.isBlacklisted(loginId)).contains(true);
      assertThat(redis.getExpire(revokedKey(loginId))).isGreaterThan(30L);
    } finally {
      deleteKeys(loginId);
    }
  }

  @Test
  @DisplayName("리프레시 토큰 키가 없어도 Access Token 만료까지 블랙리스트를 유지할 수 있다.")
  void revokeWithoutRefreshToken() {
    UUID loginId = UUID.randomUUID();
    Instant accessExpiry = Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.SECONDS);
    try {
      assertThat(tokenRepository.revoke(loginId, accessExpiry)).isEqualTo(1L);

      assertThat(tokenRepository.isBlacklisted(loginId)).contains(true);
      assertThat(redis.getExpire(revokedKey(loginId))).isBetween(1L, 60L);
    } finally {
      deleteKeys(loginId);
    }
  }

  @Test
  @DisplayName("사용자나 토큰 해시가 일치하지 않으면 리프레시 토큰 교체를 거절할 수 있다.")
  void rejectMismatchedToken() {
    UUID loginId = UUID.randomUUID();
    Instant expiresAt = Instant.now().plusSeconds(120).truncatedTo(ChronoUnit.SECONDS);
    try {
      tokenRepository.save(1L, loginId, "first-hash", expiresAt);

      assertThat(tokenRepository.rotate(2L, loginId, "first-hash", "second-hash", expiresAt))
          .isEqualTo(RedisTokenRepository.Rotation.INVALID);
      assertThat(tokenRepository.rotate(1L, loginId, "wrong-hash", "second-hash", expiresAt))
          .isEqualTo(RedisTokenRepository.Rotation.INVALID);
      assertThat(tokenRepository.rotate(1L, loginId, "first-hash", "second-hash", expiresAt))
          .isEqualTo(RedisTokenRepository.Rotation.ROTATED);
    } finally {
      deleteKeys(loginId);
    }
  }

  @Test
  @DisplayName("동시 재발급 요청 중 한 건만 토큰을 교체하고 재사용된 토큰은 블랙리스트에 등록할 수 있다.")
  void concurrentRotation() throws Exception {
    UUID loginId = UUID.randomUUID();
    Instant expiresAt = Instant.now().plusSeconds(120).truncatedTo(ChronoUnit.SECONDS);
    tokenRepository.save(1L, loginId, "first-hash", expiresAt);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var first =
          executor.submit(() -> {
            start.await();
            return tokenRepository.rotate(1L, loginId, "first-hash", "second-hash", expiresAt);
          });
      var second =
          executor.submit(() -> {
            start.await();
            return tokenRepository.rotate(1L, loginId, "first-hash", "third-hash", expiresAt);
          });
      start.countDown();

      assertThat(Set.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(
              RedisTokenRepository.Rotation.ROTATED, RedisTokenRepository.Rotation.REUSED);
      assertThat(tokenRepository.isBlacklisted(loginId)).contains(true);
    } finally {
      deleteKeys(loginId);
    }
  }

  private String refreshKey(UUID loginId) {
    return "auth:refresh:{" + loginId + "}";
  }

  private String revokedKey(UUID loginId) {
    return "auth:revoked:{" + loginId + "}";
  }

  private void deleteKeys(UUID loginId) {
    redis.delete(refreshKey(loginId));
    redis.delete(revokedKey(loginId));
  }
}
