package com.codeiary.domain.github.service;

import com.codeiary.domain.blog.event.BlogPostChanged;
import com.codeiary.domain.blog.fixture.BlogPostFixture;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.blog.service.BlogPostService;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitHubBlogSyncTransactionTest extends IntegrationTestSupport {
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;
    @Autowired BlogPostService blogPosts;
    @Autowired BlogPostRepository posts;
    @Autowired UserRepository users;
    @MockitoBean GitHubBlogSyncService sync;

    @Test
    void syncRunsImmediatelyOnRequestThreadInsideTransaction() {
        var event = new BlogPostChanged(42L, 1L);
        var thread = Thread.currentThread();
        doAnswer(invocation -> {
            assertThat(Thread.currentThread()).isSameAs(thread);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return null;
        }).when(sync).sync(event);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(event);
            verify(sync).sync(event);
        });
    }

    @Test
    void githubFailurePropagatesAndRollsBackCreatedPost() {
        var user = users.saveAndFlush(UserFixture.create(UUID.randomUUID() + "@example.com"));
        doThrow(new IllegalStateException("GitHub unavailable")).when(sync).sync(any());
        try {
            assertThatThrownBy(() -> blogPosts.create(user, BlogPostFixture.request()))
                    .isInstanceOf(IllegalStateException.class).hasMessage("GitHub unavailable");
            var event = ArgumentCaptor.forClass(BlogPostChanged.class);
            verify(sync).sync(event.capture());
            assertThat(posts.findById(event.getValue().postId())).isEmpty();
        } finally {
            users.deleteById(user.getId());
        }
    }
}
