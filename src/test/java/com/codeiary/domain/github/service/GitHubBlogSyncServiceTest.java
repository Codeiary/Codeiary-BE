package com.codeiary.domain.github.service;

import com.codeiary.domain.blog.event.BlogPostChanged;
import com.codeiary.domain.blog.fixture.BlogPostFixture;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.github.client.GitHubBlogClient;
import com.codeiary.domain.github.entity.GitHubConnection;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import com.codeiary.domain.user.fixture.UserFixture;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class GitHubBlogSyncServiceTest {
    private final BlogPostRepository posts = mock(BlogPostRepository.class);
    private final GitHubConnectionRepository connections = mock(GitHubConnectionRepository.class);
    private final GitHubBlogClient client = mock(GitHubBlogClient.class);
    private final GitHubBlogSyncService service = new GitHubBlogSyncService(posts, connections, client);

    @Test
    void readsLatestCommittedContentRatherThanEventSnapshot() {
        var user = UserFixture.createWithId();
        var connection = connected();
        var post = BlogPostFixture.createWithId(user, true);
        post.update("변경된 제목", "최신 본문", post.getCategory(), null, true);
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        service.sync(new BlogPostChanged(post.getId(), user.getId()));
        verify(client).sync(connection, post.getId(), "# 변경된 제목\n\n최신 본문\n");
    }

    @Test
    void privatePostNeverSendsBody() {
        var connection = connected();
        var post = BlogPostFixture.createWithId(UserFixture.createWithId(), false);
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        service.sync(new BlogPostChanged(post.getId(), 1L));
        verify(client).sync(connection, post.getId(), null);
    }

    @Test
    void deletedPostRemovesPreviouslySyncedFile() {
        var connection = connected();
        service.sync(new BlogPostChanged(42L, 1L));
        verify(client).sync(connection, 42L, null);
    }

    @Test
    void disconnectedUserDoesNotCallGithub() {
        service.sync(new BlogPostChanged(42L, 1L));
        verifyNoInteractions(posts, client);
    }

    private GitHubConnection connected() {
        var connection = GitHubConnection.create(UserFixture.createWithId(), 123L);
        connection.connectRepository(10L, 20L, "octocat", "CodeiaryBlog", "master");
        when(connections.findByUserId(1L)).thenReturn(Optional.of(connection));
        return connection;
    }
}
