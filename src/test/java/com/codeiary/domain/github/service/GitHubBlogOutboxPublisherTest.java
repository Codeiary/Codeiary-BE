package com.codeiary.domain.github.service;

import com.codeiary.domain.github.config.GitHubBlogSyncConfig;
import com.codeiary.domain.github.scheduler.GitHubBlogOutboxPublisher;
import com.codeiary.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitHubBlogOutboxPublisherTest extends IntegrationTestSupport {
    @Autowired GitHubBlogOutboxService dispatch;
    @Autowired RedisTemplate<String, String> redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired GitHubBlogSyncConfig settings;

    @Test
    void publishesOnlyDueBatchAndMarksPublishedRows() {
        long authorId = System.nanoTime();
        String streamKey = "test:blog:outbox:" + UUID.randomUUID();
        try {
            insert(authorId, 0);
            insert(authorId, 0);
            insert(authorId, 0);
            insert(authorId, 3600);
            var publisher = new GitHubBlogOutboxPublisher(dispatch, redis, config(streamKey, 2));

            publisher.publishDue();

            assertThat(count(authorId, "ENQUEUED")).isEqualTo(2);
            assertThat(redis.opsForStream().size(streamKey)).isEqualTo(2);
            publisher.publishDue();
            assertThat(count(authorId, "ENQUEUED")).isEqualTo(3);
            assertThat(count(authorId, "PENDING")).isEqualTo(1);
            assertThat(redis.opsForStream().size(streamKey)).isEqualTo(3);
        } finally {
            jdbc.update("DELETE FROM github_blog_outbox WHERE author_id = ?", authorId);
            redis.delete(streamKey);
        }
    }

    @Test
    void redisFailureKeepsWorkPendingForRetryOutsideTransaction() {
        long authorId = System.nanoTime();
        try {
            insert(authorId, 0);
            @SuppressWarnings("unchecked")
            RedisTemplate<String, String> failedRedis = mock(RedisTemplate.class);
            when(failedRedis.opsForStream()).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                throw new IllegalStateException("Redis unavailable");
            });

            new GitHubBlogOutboxPublisher(dispatch, failedRedis, config("test:blog:outbox", 1))
                    .publishDue();

            assertThat(count(authorId, "PENDING")).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT claim_version FROM github_blog_outbox WHERE author_id = ?
                    """, Integer.class, authorId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT failure_count FROM github_blog_outbox WHERE author_id = ?
                    """, Integer.class, authorId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT next_attempt_at > CURRENT_TIMESTAMP
                      FROM github_blog_outbox WHERE author_id = ?
                    """, Boolean.class, authorId)).isTrue();
        } finally {
            jdbc.update("DELETE FROM github_blog_outbox WHERE author_id = ?", authorId);
        }
    }

    @Test
    void stopsPublishingAfterMaximumFailures() {
        long authorId = System.nanoTime();
        try {
            insert(authorId, 0);
            jdbc.update("""
                    UPDATE github_blog_outbox SET failure_count = ? WHERE author_id = ?
                    """, settings.getMaxFailures() - 1, authorId);
            @SuppressWarnings("unchecked")
            RedisTemplate<String, String> failedRedis = mock(RedisTemplate.class);
            when(failedRedis.opsForStream()).thenThrow(new IllegalStateException("Redis unavailable"));

            var publisher = new GitHubBlogOutboxPublisher(dispatch, failedRedis, config("test:blog:outbox", 1));
            publisher.publishDue();
            publisher.publishDue();

            assertThat(count(authorId, "FAILED")).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT failure_count FROM github_blog_outbox WHERE author_id = ?
                    """, Integer.class, authorId)).isEqualTo(settings.getMaxFailures());
        } finally {
            jdbc.update("DELETE FROM github_blog_outbox WHERE author_id = ?", authorId);
        }
    }

    @Test
    void expiredDeliveryReachesFailedAtMaximumFailures() {
        long authorId = System.nanoTime();
        String streamKey = "test:blog:outbox:" + UUID.randomUUID();
        try {
            insert(authorId, 0);
            jdbc.update("""
                    UPDATE github_blog_outbox
                       SET status = 'ENQUEUED', failure_count = ?
                     WHERE author_id = ?
                    """, settings.getMaxFailures() - 1, authorId);

            new GitHubBlogOutboxPublisher(dispatch, redis, config(streamKey, 1)).publishDue();

            assertThat(count(authorId, "FAILED")).isEqualTo(1);
            assertThat(redis.opsForStream().size(streamKey)).isZero();
        } finally {
            jdbc.update("DELETE FROM github_blog_outbox WHERE author_id = ?", authorId);
            redis.delete(streamKey);
        }
    }

    @Test
    void republishesExpiredEnqueuedAndProcessingWork() {
        long authorId = System.nanoTime();
        String streamKey = "test:blog:outbox:" + UUID.randomUUID();
        try {
            insert(authorId, 0);
            insert(authorId, 0);
            jdbc.update("""
                    UPDATE github_blog_outbox
                       SET status = 'ENQUEUED', next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 day'
                     WHERE author_id = ? AND id = (
                           SELECT MIN(id) FROM github_blog_outbox WHERE author_id = ?)
                    """, authorId, authorId);
            jdbc.update("""
                    UPDATE github_blog_outbox
                       SET status = 'PROCESSING', next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 day'
                     WHERE author_id = ? AND id = (
                           SELECT MAX(id) FROM github_blog_outbox WHERE author_id = ?)
                    """, authorId, authorId);

            new GitHubBlogOutboxPublisher(dispatch, redis, config(streamKey, 2)).publishDue();

            assertThat(count(authorId, "ENQUEUED")).isEqualTo(2);
            assertThat(redis.opsForStream().size(streamKey)).isEqualTo(2);
        } finally {
            jdbc.update("DELETE FROM github_blog_outbox WHERE author_id = ?", authorId);
            redis.delete(streamKey);
        }
    }

    private void insert(long authorId, int delaySeconds) {
        jdbc.update("""
                INSERT INTO github_blog_outbox
                    (post_id, author_id, event_type, next_attempt_at)
                VALUES (?, ?, 'CREATED', CURRENT_TIMESTAMP + (? * INTERVAL '1 second'))
                """, System.nanoTime(), authorId, delaySeconds);
    }

    private GitHubBlogSyncConfig config(String streamKey, int batchSize) {
        var config = new GitHubBlogSyncConfig();
        config.setStreamKey(streamKey);
        config.setPublishBatchSize(batchSize);
        return config;
    }

    private long count(long authorId, String status) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM github_blog_outbox WHERE author_id = ? AND status = ?
                """, Long.class, authorId, status);
    }
}
