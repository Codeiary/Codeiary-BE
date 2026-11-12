package com.codeiary.domain.github.service;

import com.codeiary.domain.blog.fixture.BlogPostFixture;
import com.codeiary.domain.blog.service.BlogPostService;
import com.codeiary.domain.github.client.GitHubBlogWriter;
import com.codeiary.domain.github.config.GitHubBlogSyncConfig;
import com.codeiary.domain.github.entity.GitHubConnection;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import com.codeiary.domain.github.scheduler.GitHubBlogOutboxConsumer;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.support.IntegrationTestSupport;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GitHubBlogOutboxConsumerTest extends IntegrationTestSupport {
    @Autowired BlogPostService blogPosts;
    @Autowired UserRepository users;
    @Autowired GitHubConnectionRepository connections;
    @Autowired GitHubBlogOutboxService dispatch;
    @Autowired RedisTemplate<String, String> redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired GitHubBlogSyncConfig settings;
    @MockitoBean GitHubBlogWriter writer;

    @Test
    void recoversPendingMessageAndCallsGithubOutsideTransaction() {
        var setup = setup();
        try {
            doAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                return null;
            }).when(writer).sync(any());
            redis.opsForStream().createGroup(setup.streamKey(), ReadOffset.from("0-0"), setup.group());
            redis.opsForStream().read(Consumer.from(setup.group(), "stopped-consumer"),
                    StreamReadOptions.empty().count(1),
                    StreamOffset.create(setup.streamKey(), ReadOffset.lastConsumed()));

            var consumer = consumer(setup, Duration.ZERO);
            consumer.consume();

            assertThat(status(setup.id())).isEqualTo("COMPLETED");
            assertThat(redis.opsForStream().size(setup.streamKey())).isZero();
            redis.opsForStream().add(setup.streamKey(), Map.of("outboxId", Long.toString(setup.id())));
            consumer.consume();
            assertThat(redis.opsForStream().size(setup.streamKey())).isZero();
            verify(writer, times(1)).sync(any());
        } finally {
            cleanup(setup);
        }
    }

    @Test
    void githubFailureReturnsWorkToPendingAndAcknowledgesMessage() {
        var setup = setup();
        try {
            doThrow(new IllegalStateException("GitHub unavailable"))
                    .when(writer).sync(any());

            consumer(setup, Duration.ofMinutes(2)).consume();

            assertThat(status(setup.id())).isEqualTo("PENDING");
            assertThat(redis.opsForStream().size(setup.streamKey())).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT next_attempt_at > CURRENT_TIMESTAMP
                      FROM github_blog_outbox WHERE id = ?
                    """, Boolean.class, setup.id())).isTrue();
        } finally {
            cleanup(setup);
        }
    }

    @Test
    void stopsConsumingAfterMaximumGithubFailures() {
        var setup = setup();
        try {
            jdbc.update("""
                    UPDATE github_blog_outbox SET failure_count = ? WHERE id = ?
                    """, settings.getMaxFailures() - 1, setup.id());
            doThrow(new IllegalStateException("GitHub unavailable"))
                    .when(writer).sync(any());

            consumer(setup, Duration.ofMinutes(2)).consume();

            assertThat(status(setup.id())).isEqualTo("FAILED");
            assertThat(redis.opsForStream().size(setup.streamKey())).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT failure_count FROM github_blog_outbox WHERE id = ?
                    """, Integer.class, setup.id())).isEqualTo(settings.getMaxFailures());
        } finally {
            cleanup(setup);
        }
    }

    private Setup setup() {
        var user = users.saveAndFlush(UserFixture.create(UUID.randomUUID() + "@example.com"));
        var connection = GitHubConnection.create(user, ThreadLocalRandom.current().nextLong(10_000, Long.MAX_VALUE));
        connection.connect("octocat");
        connection.connectRepository(10L, 20L, "octocat", "CodeiaryBlog", "master");
        connections.saveAndFlush(connection);
        var post = blogPosts.create(user, BlogPostFixture.request());
        Long id = jdbc.queryForObject("""
                SELECT id FROM github_blog_outbox WHERE post_id = ? ORDER BY id DESC LIMIT 1
                """, Long.class, post.id());
        String streamKey = "test:blog:consumer:" + UUID.randomUUID();
        String group = "test-group:" + UUID.randomUUID();
        jdbc.update("UPDATE github_blog_outbox SET status = 'ENQUEUED' WHERE id = ?", id);
        redis.opsForStream().add(streamKey, Map.of("outboxId", id.toString()));
        return new Setup(id, user.getId(), streamKey, group);
    }

    private GitHubBlogOutboxConsumer consumer(Setup setup, Duration reclaimAfter) {
        var config = new GitHubBlogSyncConfig();
        config.setStreamKey(setup.streamKey());
        config.setConsumerGroup(setup.group());
        config.setConsumeBatchSize(20);
        config.setReclaimAfter(reclaimAfter);
        return new GitHubBlogOutboxConsumer(dispatch, writer, redis, config);
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM github_blog_outbox WHERE id = ?", String.class, id);
    }

    private void cleanup(Setup setup) {
        redis.delete(setup.streamKey());
        jdbc.update("DELETE FROM github_blog_outbox WHERE id = ?", setup.id());
        users.deleteById(setup.userId());
    }

    private record Setup(long id, long userId, String streamKey, String group) {
    }
}
