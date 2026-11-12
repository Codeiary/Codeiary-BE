package com.codeiary.domain.github.client;

import com.codeiary.domain.github.entity.GitHubConnection;
import com.codeiary.domain.github.dto.outbox.GitHubBlogSyncContent;
import com.codeiary.domain.github.entity.enums.EventType;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.global.exception.RestApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitHubBlogClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GitHubAppTokenProvider tokens = mock(GitHubAppTokenProvider.class);
    private final GitHubBlogClient client = new GitHubBlogClient(builder.build(), tokens);
    private final GitHubConnection connection = GitHubConnection.create(UserFixture.createWithId(), 123L);
    private final String path = "Java/한글 제목-42.md";
    private final String url = url(path);
    private final String markdown = "# 한글 제목\n\n```java\nint a = 1;\n```\n";

    @BeforeEach
    void setup() {
        connection.connectRepository(10L, 20L, "octocat", "CodeiaryBlog", "master");
        when(tokens.token(10, 20)).thenReturn("installation-token");
    }

    @Test
    void createsUtf8MarkdownOnStoredBranchUsingInstallationToken() {
        server.expect(requestTo(url + "?ref=master"))
                .andExpect(header("Authorization", "Bearer installation-token"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(url)).andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.message").value("한글 제목-create"))
                .andExpect(jsonPath("$.branch").value("master"))
                .andExpect(jsonPath("$.content").value(encoded()))
                .andExpect(jsonPath("$.sha").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED));
        client.sync(content(EventType.CREATED, markdown, path, List.of()));
        server.verify();
    }

    @Test
    void updateConflictRefetchesShaBeforeOneRetry() {
        expectFile("old");
        server.expect(requestTo(url)).andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.sha").value("old"))
                .andExpect(jsonPath("$.message").value("한글 제목-update"))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        expectFile("new");
        server.expect(requestTo(url)).andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.sha").value("new")).andRespond(withSuccess());
        client.sync(content(EventType.UPDATED, markdown, path, List.of()));
        server.verify();
    }

    @Test
    void renameWritesNewFileAndRemovesOldAndLegacyPaths() {
        String oldPath = "Spring/이전 제목-42.md";
        String oldUrl = url(oldPath);
        String legacyPath = "posts/42.md";
        server.expect(requestTo(url + "?ref=master")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(url)).andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.message").value("한글 제목-update"))
                .andRespond(withStatus(HttpStatus.CREATED));
        server.expect(requestTo(oldUrl + "?ref=master")).andRespond(withSuccess(
                "{\"sha\":\"old\",\"encoding\":\"base64\",\"content\":\"b2xk\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(oldUrl)).andExpect(method(HttpMethod.DELETE))
                .andExpect(jsonPath("$.message").value("한글 제목-update"))
                .andExpect(jsonPath("$.sha").value("old"))
                .andRespond(withSuccess());
        server.expect(requestTo(url(legacyPath) + "?ref=master"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        client.sync(content(EventType.UPDATED, markdown, path, List.of(oldPath, legacyPath)));
        server.verify();
    }

    @Test
    void privateOrDeletedPostRemovesCurrentFileWithoutUploadingContent() {
        expectFile("old");
        server.expect(requestTo(url)).andExpect(method(HttpMethod.DELETE))
                .andExpect(jsonPath("$.sha").value("old"))
                .andExpect(jsonPath("$.message").value("한글 제목-delete"))
                .andExpect(jsonPath("$.content").doesNotExist()).andRespond(withSuccess());
        client.sync(content(EventType.DELETED, null, null, List.of(path)));
        server.verify();
    }

    @Test
    void unchangedMarkdownDoesNotCreateCommit() {
        server.expect(requestTo(url + "?ref=master")).andRespond(withSuccess(
                "{\"sha\":\"same\",\"encoding\":\"base64\",\"content\":\"" + encoded() + "\\n\"}", MediaType.APPLICATION_JSON));
        client.sync(content(EventType.UPDATED, markdown, path, List.of()));
        server.verify();
    }

    @Test
    void missingPrivatePostDoesNotCreateFile() {
        server.expect(requestTo(url + "?ref=master")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        client.sync(content(EventType.UPDATED, null, null, List.of(path)));
        server.verify();
    }

    @Test
    void upstreamErrorDoesNotLeakResponseOrToken() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN).body("private-upstream-data"));
        assertThatThrownBy(() -> client.sync(content(EventType.CREATED, markdown, path, List.of())))
                .isInstanceOfSatisfying(RestApiException.class, error -> {
                    assertThat(error.getCause()).isNull();
                    assertThat(error.getMessage()).doesNotContain("private-upstream-data", "installation-token");
                });
        server.verify();
    }

    private String encoded() {
        return Base64.getEncoder().encodeToString(markdown.getBytes(StandardCharsets.UTF_8));
    }

    private GitHubBlogSyncContent content(EventType type, String body, String currentPath, List<String> obsolete) {
        return new GitHubBlogSyncContent(connection, "한글 제목", type, body, currentPath, obsolete);
    }

    private String url(String filePath) {
        String[] parts = filePath.split("/", 2);
        return "https://api.github.com/repositories/20/contents/"
                + UriUtils.encodePathSegment(parts[0], StandardCharsets.UTF_8) + "/"
                + UriUtils.encodePathSegment(parts[1], StandardCharsets.UTF_8);
    }

    private void expectFile(String sha) {
        server.expect(requestTo(url + "?ref=master")).andRespond(withSuccess(
                "{\"sha\":\"" + sha + "\",\"encoding\":\"base64\",\"content\":\"b2xk\"}", MediaType.APPLICATION_JSON));
    }
}
