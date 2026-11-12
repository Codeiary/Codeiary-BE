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
    private final GitHubBlogOutboxService outbox;

    private final GitHubBlogWriter writer;
    private final RedisTemplate<String, String> redis;
    private final GitHubBlogSyncConfig config;
    private final String consumerName = UUID.randomUUID().toString();
    private boolean groupReady;


    @Scheduled(fixedDelayString = "#{@githubBlogSyncConfig.consumeIntervalMs}")
    public void consume() {
        try {
            ensureGroup();
            var recovered = reclaimPending();
            recovered.forEach(this::process);

            int remaining = config.getConsumeBatchSize() - recovered.size();
            if (remaining > 0) {
            	
                @SuppressWarnings("unchecked")
				var newMessages = redis.opsForStream().read(Consumer.from(config.getConsumerGroup(), consumerName),
                        StreamReadOptions.empty().count(remaining),
                        StreamOffset.create(config.getStreamKey(), ReadOffset.lastConsumed()));
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
            redis.opsForStream().createGroup(config.getStreamKey(), ReadOffset.from("0-0"), config.getConsumerGroup());
        } catch (DataAccessException exception) {
            if (!redisErrorIs(exception, "BUSYGROUP")) throw exception;
        }
        groupReady = true;
    }

    private List<MapRecord<String, Object, Object>> reclaimPending() {
        var pending = redis.opsForStream().pending(config.getStreamKey(), config.getConsumerGroup(),
                Range.unbounded(), config.getConsumeBatchSize());
        if (pending == null || pending.isEmpty()) return List.of();

        var ids = new ArrayList<RecordId>();
        for (var message : pending) {
            if (message.getElapsedTimeSinceLastDelivery().compareTo(config.getReclaimAfter()) >= 0) {
                ids.add(message.getId());
            }
        }
        if (ids.isEmpty()) return List.of();
        return redis.opsForStream().claim(config.getStreamKey(), config.getConsumerGroup(), consumerName,
                config.getReclaimAfter(),
                ids.toArray(RecordId[]::new));
    }

    private void process(MapRecord<String, Object, Object> message) {
        long id;
        try {
            id = Long.parseLong((String) message.getValue().get(OUTBOX_ID));
        } catch (RuntimeException exception) {
            log.warn("Invalid GitHub blog stream message: streamId={}", message.getId(), exception);
            acknowledge(message);
            return;
        }

        var claimed = outbox.claimForProcessing(id);
        if (claimed.isEmpty()) {
            if (outbox.isFinishedOrMissing(id)) acknowledge(message);
            return;
        }

        var work = claimed.get();
        try {
            sync(work);
        } catch (RuntimeException exception) {
            log.warn("GitHub blog sync failed: outboxId={}", id, exception);
            if (outbox.retryProcessing(work)) acknowledge(message);
            return;
        }
        if (outbox.markCompleted(work)) acknowledge(message);
    }

    private void acknowledge(MapRecord<String, Object, Object> message) {
        Long count = redis.opsForStream().acknowledge(config.getStreamKey(), config.getConsumerGroup(), message.getId());
        if (count != null && count > 0) {
            redis.opsForStream().delete(config.getStreamKey(), message.getId());
        }
    }

    private boolean redisErrorIs(DataAccessException exception, String code) {
        return String.valueOf(exception.getMostSpecificCause().getMessage()).contains(code);
    }
    
    public void sync(ProcessingWork work) {
        var content = outbox.read(work);
        if (content != null) {
            writer.sync(content);
        }
    }
}
