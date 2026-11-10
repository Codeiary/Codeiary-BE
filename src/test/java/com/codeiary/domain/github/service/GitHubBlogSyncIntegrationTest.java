package com.codeiary.domain.github.service;

import com.codeiary.domain.blog.dto.request.BlogPostRequest;
import com.codeiary.domain.blog.fixture.BlogPostFixture;
import com.codeiary.domain.blog.service.BlogPostService;
import com.codeiary.domain.github.client.GitHubBlogClient;
import com.codeiary.domain.github.entity.GitHubConnection;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Transactional
class GitHubBlogSyncIntegrationTest extends IntegrationTestSupport {
    @Autowired BlogPostService blogPosts;
    @Autowired UserRepository users;
    @Autowired GitHubConnectionRepository connections;
    @MockitoBean GitHubBlogClient client;

    @Test
    void createUpdatePrivateAndDeleteReadCurrentStateWithinSameTransaction() {
        var user = users.saveAndFlush(UserFixture.create());
        var connection = GitHubConnection.create(user, 123L);
        connection.connect("octocat");
        connection.connectRepository(10L, 20L, "octocat", "CodeiaryBlog", "master");
        connections.saveAndFlush(connection);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return null;
        }).when(client).sync(any(), anyLong(), nullable(String.class));

        var created = blogPosts.create(user, BlogPostFixture.request());
        verify(client).sync(any(), eq(created.id()), eq("# 첫 기록\n\n" + BlogPostFixture.CONTENT + "\n"));
        blogPosts.update(created.id(), user, new BlogPostRequest("수정", "최신 본문", "Java", List.of(), null, true));
        verify(client).sync(any(), eq(created.id()), eq("# 수정\n\n최신 본문\n"));
        blogPosts.update(created.id(), user, new BlogPostRequest("수정", "비공개 본문", "Java", List.of(), null, false));
        verify(client).sync(any(), eq(created.id()), isNull());
        blogPosts.delete(created.id(), user);
        verify(client, times(2)).sync(any(), eq(created.id()), isNull());
    }
}
