package com.codeiary.domain.github.scheduler;

import com.codeiary.domain.github.client.GitHubBlogWriter;
import com.codeiary.domain.github.config.GitHubBlogSyncConfig;
import com.codeiary.domain.github.dto.outbox.ProcessingWork;
import com.codeiary.domain.github.service.GitHubBlogOutboxService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubBlogOutboxConsumer {
  private static final String OUTBOX_ID = "outboxId";
  private final GitHubBlogOutboxService gitHubBlogOutboxService;

  private final GitHubBlogWriter gitHubBlogWriter;
  private final RedisTemplate<String, String> redisTemplate;
  private final GitHubBlogSyncConfig gitHubBlogSyncConfig;
  private final String consumerName = UUID.randomUUID().toString();
  private boolean groupReady;

  @Scheduled(fixedDelayString = "#{@githubBlogSyncConfig.consumeIntervalMs}")
  public void consume() {
    try {
      ensureGroup();
      var recovered = reclaimPending();
      recovered.forEach(this::process);

      int remaining = gitHubBlogSyncConfig.getConsumeBatchSize() - recovered.size();
      if (remaining > 0) {

        @SuppressWarnings("unchecked")
        var newMessages =
            redisTemplate
                .opsForStream()
                .read(
                    Consumer.from(gitHubBlogSyncConfig.getConsumerGroup(), consumerName),
                    StreamReadOptions.empty().count(remaining),
                    StreamOffset.create(
                        gitHubBlogSyncConfig.getStreamKey(), ReadOffset.lastConsumed()));
        if (newMessages != null) {
          newMessages.forEach(this::process);
        }
      }
    } catch (DataAccessException exception) {
      if (redisErrorIs(exception, "NOGROUP")) {
        groupReady = false;
      }
      log.warn("GitHub blog outbox consume failed", exception);
    }
  }

  private void ensureGroup() {
    if (groupReady) return;
    try {
      redisTemplate
          .opsForStream()
          .createGroup(
              gitHubBlogSyncConfig.getStreamKey(),
              ReadOffset.from("0-0"),
              gitHubBlogSyncConfig.getConsumerGroup());
    } catch (DataAccessException exception) {
      if (!redisErrorIs(exception, "BUSYGROUP")) throw exception;
    }
    groupReady = true;
  }

  private List<MapRecord<String, Object, Object>> reclaimPending() {
    var pending =
        redisTemplate
            .opsForStream()
            .pending(
                gitHubBlogSyncConfig.getStreamKey(),
                gitHubBlogSyncConfig.getConsumerGroup(),
                Range.unbounded(),
                gitHubBlogSyncConfig.getConsumeBatchSize());
    if (pending == null || pending.isEmpty()) return List.of();

    var ids = new ArrayList<RecordId>();
    for (var message : pending) {
      if (message
              .getElapsedTimeSinceLastDelivery()
              .compareTo(gitHubBlogSyncConfig.getReclaimAfter())
          >= 0) {
        ids.add(message.getId());
      }
    }
    if (ids.isEmpty()) return List.of();
    return redisTemplate
        .opsForStream()
        .claim(
            gitHubBlogSyncConfig.getStreamKey(),
            gitHubBlogSyncConfig.getConsumerGroup(),
            consumerName,
            gitHubBlogSyncConfig.getReclaimAfter(),
            ids.toArray(RecordId[]::new));
  }

  private void process(MapRecord<String, Object, Object> mapRecord) {
    long id;
    try {
      id = Long.parseLong((String) mapRecord.getValue().get(OUTBOX_ID));
    } catch (RuntimeException exception) {
      log.warn("Invalid GitHub blog stream message: streamId={}", mapRecord.getId(), exception);
      acknowledge(mapRecord);
      return;
    }

    var claimed = gitHubBlogOutboxService.claimForProcessing(id);
    if (claimed.isEmpty()) {
      if (gitHubBlogOutboxService.isFinishedOrMissing(id)) acknowledge(mapRecord);
      return;
    }

    var work = claimed.get();
    try {
      sync(work);
    } catch (RuntimeException exception) {
      log.warn("GitHub blog sync failed: outboxId={}", id, exception);
      if (gitHubBlogOutboxService.retryProcessing(work)) acknowledge(mapRecord);
      return;
    }
    if (gitHubBlogOutboxService.markCompleted(work)) acknowledge(mapRecord);
  }

  private void acknowledge(MapRecord<String, Object, Object> mapRecord) {
    Long count =
        redisTemplate
            .opsForStream()
            .acknowledge(
                gitHubBlogSyncConfig.getStreamKey(),
                gitHubBlogSyncConfig.getConsumerGroup(),
                mapRecord.getId());
    if (count != null && count > 0) {
      redisTemplate.opsForStream().delete(gitHubBlogSyncConfig.getStreamKey(), mapRecord.getId());
    }
  }

  private boolean redisErrorIs(DataAccessException dataAccessException, String code) {
    return String.valueOf(dataAccessException.getMostSpecificCause().getMessage()).contains(code);
  }

  public void sync(ProcessingWork processingWork) {
    var content = gitHubBlogOutboxService.read(processingWork);
    if (content != null) {
      gitHubBlogWriter.sync(content);
    }
  }
}
