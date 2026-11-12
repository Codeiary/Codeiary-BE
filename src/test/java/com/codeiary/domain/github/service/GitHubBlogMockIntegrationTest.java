package com.codeiary.domain.github.service;

import com.codeiary.domain.blog.dto.request.BlogPostRequest;
import com.codeiary.domain.blog.fixture.BlogPostFixture;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.blog.service.BlogPostService;
import com.codeiary.domain.github.client.GitHubAppTokenProvider;
import com.codeiary.domain.github.client.GitHubBlogWriter;
import com.codeiary.domain.github.client.MockGitHubBlogClient;
import com.codeiary.domain.github.entity.GitHubConnection;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ActiveProfiles({"test", "loadtest"})
@TestPropertySource(properties = "github.mock-delay=0ms")
@Transactional
class GitHubBlogMockIntegrationTest extends IntegrationTestSupport {
    @Autowired BlogPostService blogPosts;
    @Autowired BlogPostRepository posts;
    @Autowired UserRepository users;
    @Autowired GitHubConnectionRepository connections;
    @Autowired GitHubBlogWriter writer;
    @MockitoBean GitHubAppTokenProvider tokens;

    @Test
    void loadtestUsesMockForSaveAndUpdateWithoutGithubCredentials() {
        assertThat(AopUtils.getTargetClass(writer)).isEqualTo(MockGitHubBlogClient.class);
        var user = users.saveAndFlush(UserFixture.create());
        var connection = GitHubConnection.create(user, 123L);
        connection.connect("octocat");
        connection.connectRepository(10L, 20L, "octocat", "CodeiaryBlog", "master");
        connections.saveAndFlush(connection);

        var created = blogPosts.create(user, BlogPostFixture.request());
        blogPosts.update(created.id(), user, new BlogPostRequest("수정", "수정 본문", "Java", List.of(), null, true));
        posts.flush();
        assertThat(posts.findById(created.id()).orElseThrow().getContent()).isEqualTo("수정 본문");
        verifyNoInteractions(tokens);
    }
}
