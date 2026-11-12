package com.codeiary.domain.github.service;

import com.codeiary.domain.blog.dto.request.BlogPostRequest;
import com.codeiary.domain.blog.fixture.BlogPostFixture;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.blog.service.BlogPostService;
import com.codeiary.domain.github.client.GitHubBlogWriter;
import com.codeiary.domain.github.dto.outbox.ProcessingWork;
import com.codeiary.domain.github.entity.enums.EventType;
import com.codeiary.domain.github.entity.enums.OutBoxStatus;
import com.codeiary.domain.github.entity.GitHubConnection;
import com.codeiary.domain.github.repository.GitHubBlogOutboxRepository;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.support.IntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

class GitHubBlogOutboxIntegrationTest extends IntegrationTestSupport {
    @Autowired BlogPostService blogPosts;
    @Autowired BlogPostRepository posts;
    @Autowired UserRepository users;
    @Autowired GitHubConnectionRepository connections;
    @Autowired GitHubBlogOutboxRepository outbox;
    @Autowired GitHubBlogOutboxService syncStatus;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean GitHubBlogWriter writer;

    @Test
    void createUpdateAndDeletePersistPendingWorkWithoutCallingGithub() {
        var user = users.saveAndFlush(UserFixture.create(UUID.randomUUID() + "@example.com"));
        var connection = GitHubConnection.create(user, 123L);
        connection.connect("octocat");
        connection.connectRepository(10L, 20L, "octocat", "CodeiaryBlog", "master");
        connections.saveAndFlush(connection);

        var created = blogPosts.create(user, BlogPostFixture.request());
        var initialStatus = syncStatus.statusFor(created.id(), user.getId());
        assertThat(initialStatus.connected()).isTrue();
        assertThat(initialStatus.status()).isEqualTo(OutBoxStatus.PENDING);
        blogPosts.update(created.id(), user,
                new BlogPostRequest("수정", "본문", "Spring", List.of(), null, true));
        var pendingAfterRename = outbox.findAll(Sort.by("id")).stream()
                .filter(item -> item.getAuthorId().equals(user.getId()))
                .toList();
        var currentContent = syncStatus.read(new ProcessingWork(
                pendingAfterRename.getFirst().getId(), created.id(), user.getId(), 0));
        assertThat(currentContent.path()).isEqualTo("Spring/수정-" + created.id() + ".md");
        assertThat(currentContent.obsoletePaths()).contains("Java/첫 기록-" + created.id() + ".md");
        blogPosts.delete(created.id(), user);

        var work = outbox.findAll(Sort.by("id")).stream()
                .filter(item -> item.getAuthorId().equals(user.getId()))
                .toList();
        assertThat(work).extracting(item -> item.getEventType())
                .containsExactly(EventType.CREATED, EventType.UPDATED, EventType.DELETED);
        assertThat(work).extracting(item -> item.getTitle())
                .containsExactly("첫 기록", "수정", "수정");
        assertThat(work.get(0).getTargetPath()).isEqualTo("Java/첫 기록-" + created.id() + ".md");
        assertThat(work.get(1).getPreviousPath()).isEqualTo("Java/첫 기록-" + created.id() + ".md");
        assertThat(work.get(1).getTargetPath()).isEqualTo("Spring/수정-" + created.id() + ".md");
        assertThat(work.get(2).getPreviousPath()).isEqualTo("Spring/수정-" + created.id() + ".md");
        assertThat(work.get(2).getTargetPath()).isNull();
        assertThat(work).allSatisfy(item -> {
            assertThat(item.getPostId()).isEqualTo(created.id());
            assertThat(item.getStatus()).isEqualTo(OutBoxStatus.PENDING);
            assertThat(item.getClaimVersion()).isZero();
            assertThat(item.getFailureCount()).isZero();
        });
        verifyNoInteractions(writer);
    }

    @Test
    void rollingBackPostChangeAlsoRollsBackOutboxWork() {
        var user = users.saveAndFlush(UserFixture.create(UUID.randomUUID() + "@example.com"));
        var connection = GitHubConnection.create(user, 124L);
        connection.connect("octocat-2");
        connection.connectRepository(11L, 21L, "octocat-2", "CodeiaryBlog", "master");
        connections.saveAndFlush(connection);

        var postId = new TransactionTemplate(transactions).execute(status -> {
            var created = blogPosts.create(user, BlogPostFixture.request());
            assertThat(posts.findById(created.id())).isPresent();
            assertThat(outbox.findAll().stream()
                    .filter(item -> item.getPostId().equals(created.id()))).hasSize(1);
            status.setRollbackOnly();
            return created.id();
        });

        assertThat(posts.findById(postId)).isEmpty();
        assertThat(outbox.findAll().stream()
                .filter(item -> item.getAuthorId().equals(user.getId()))).isEmpty();
    }
}
