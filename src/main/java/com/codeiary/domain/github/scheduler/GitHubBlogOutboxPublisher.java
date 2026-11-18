package com.codeiary.domain.github.scheduler;

import com.codeiary.domain.github.config.GitHubBlogSyncConfig;
import com.codeiary.domain.github.service.GitHubBlogOutboxService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubBlogOutboxPublisher {
  private static final String OUTBOX_ID = "outboxId";

  private final GitHubBlogOutboxService gitHubBlogOutboxService;
  private final RedisTemplate<String, String> redisTemplate;
  private final GitHubBlogSyncConfig gitHubBlogSyncConfig;

  @Scheduled(fixedDelayString = "#{@githubBlogSyncConfig.publishIntervalMs}")
  public void publishDue() {
    for (var work : gitHubBlogOutboxService.claimDue(gitHubBlogSyncConfig.getPublishBatchSize())) {
      try {
        redisTemplate
            .opsForStream()
            .add(gitHubBlogSyncConfig.getStreamKey(), Map.of(OUTBOX_ID, Long.toString(work.id())));
      } catch (RuntimeException exception) {
        gitHubBlogOutboxService.retryNextCycle(work);
        log.warn("GitHub blog outbox publish failed: outboxId={}", work.id(), exception);
        continue;
      }
      gitHubBlogOutboxService.markEnqueued(work);
    }
  }
}
