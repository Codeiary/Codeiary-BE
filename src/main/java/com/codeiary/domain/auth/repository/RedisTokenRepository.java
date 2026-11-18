package com.codeiary.domain.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisTokenRepository {

  private static final DefaultRedisScript<Long> CREATE =
      script(
          """
          if redis.call('EXISTS', KEYS[1], KEYS[2]) ~= 0 then return 0 end
          redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'current', ARGV[2], 'expiresAt', ARGV[3])
          redis.call('PEXPIREAT', KEYS[1], ARGV[3])
          return 1
          """);
  private static final DefaultRedisScript<Long> ROTATE =
      script(
          """
          if redis.call('EXISTS', KEYS[2]) == 1 then return 3 end
          if redis.call('HGET', KEYS[1], 'userId') ~= ARGV[1] then return 0 end
          local expiresAt = redis.call('HGET', KEYS[1], 'expiresAt')
          if expiresAt ~= ARGV[4] then return 0 end
          if redis.call('HGET', KEYS[1], 'current') == ARGV[2] then
              redis.call('HSET', KEYS[1], 'used:' .. ARGV[2], '1', 'current', ARGV[3])
              return 1
          end
          if redis.call('HEXISTS', KEYS[1], 'used:' .. ARGV[2]) == 1 then
              redis.call('SET', KEYS[2], expiresAt, 'PXAT', expiresAt)
              redis.call('DEL', KEYS[1])
              return 2
          end
          return 0
          """);
  private static final DefaultRedisScript<Long> REVOKE =
      script(
          """
          local expiresAt = tonumber(ARGV[1])
          local activeExpiry = redis.call('HGET', KEYS[1], 'expiresAt')
          if activeExpiry then expiresAt = math.max(expiresAt, tonumber(activeExpiry)) end
          local revokedExpiry = redis.call('GET', KEYS[2])
          if revokedExpiry then expiresAt = math.max(expiresAt, tonumber(revokedExpiry)) end
          local expiry = string.format('%.0f', expiresAt)
          redis.call('SET', KEYS[2], expiry, 'PXAT', expiry)
          redis.call('DEL', KEYS[1])
          return 1
          """);

  private final RedisTemplate<String, String> redis;

  public Long save(Long userId, UUID loginId, String tokenHash, Instant expiresAt) {
    return redis.execute(
        CREATE,
        keys(loginId),
        userId.toString(),
        tokenHash,
        String.valueOf(expiresAt.toEpochMilli()));
  }

  public Optional<Boolean> isBlacklisted(UUID loginId) {
    return Optional.ofNullable(redis.hasKey(blacklistKey(loginId)));
  }

  public Rotation rotate(
      Long userId, UUID loginId, String oldHash, String newHash, Instant expiresAt) {
    Long result =
        redis.execute(
            ROTATE,
            keys(loginId),
            userId.toString(),
            oldHash,
            newHash,
            String.valueOf(expiresAt.toEpochMilli()));
    if (Long.valueOf(1).equals(result)) return Rotation.ROTATED;
    if (Long.valueOf(2).equals(result)) return Rotation.REUSED;
    if (Long.valueOf(3).equals(result)) return Rotation.REVOKED;
    return Rotation.INVALID;
  }

  public Long revoke(UUID loginId, Instant expiresAt) {
    return redis.execute(REVOKE, keys(loginId), String.valueOf(expiresAt.toEpochMilli()));
  }

  private List<String> keys(UUID loginId) {
    return List.of("auth:refresh:{" + loginId + "}", blacklistKey(loginId));
  }

  private String blacklistKey(UUID loginId) {
    return "auth:revoked:{" + loginId + "}";
  }

  private static DefaultRedisScript<Long> script(String source) {
    var script = new DefaultRedisScript<Long>();
    script.setScriptText(source);
    script.setResultType(Long.class);
    return script;
  }

  public enum Rotation {
    ROTATED,
    REUSED,
    REVOKED,
    INVALID
  }
}
