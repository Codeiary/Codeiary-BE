package com.codeiary.domain.github.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.codeiary.domain.github.dto.outbox.GitHubBlogSyncContent;
import com.codeiary.domain.github.entity.GitHubConnection;
import com.codeiary.domain.github.entity.enums.EventType;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.global.exception.RestApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

class GitHubBlogClientTest {
  private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final GitHubAppTokenProvider tokens = mock(GitHubAppTokenProvider.class);
  private final GitHubBlogClient client = new GitHubBlogClient(builder.build(), tokens);
  private final GitHubConnection connection =
      GitHubConnection.create(UserFixture.createWithId(), 123L);
  private final String path = "Java/한글 제목-42.md";
  private final String url = url(path);
  private final String markdown = "# 한글 제목\n\n```java\nint a = 1;\n```\n";

  @BeforeEach
  void setup() {
    connection.connectRepository(10L, 20L, "octocat", "CodeiaryBlog", "master");
    when(tokens.token(10, 20)).thenReturn("installation-token");
  }

  @DisplayName("설치 토큰으로 저장된 브랜치에 UTF-8 Markdown을 작성할 수 있다.")
  @Test
  void createsUtf8MarkdownOnStoredBranchUsingInstallationToken() {
    server
        .expect(requestTo(url + "?ref=master"))
        .andExpect(header("Authorization", "Bearer installation-token"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    server
        .expect(requestTo(url))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(jsonPath("$.message").value("한글 제목-create"))
        .andExpect(jsonPath("$.branch").value("master"))
        .andExpect(jsonPath("$.content").value(encoded()))
        .andExpect(jsonPath("$.sha").doesNotExist())
        .andRespond(withStatus(HttpStatus.CREATED));
    client.sync(content(EventType.CREATED, markdown, path, List.of()));
    server.verify();
  }

  @DisplayName("수정 충돌 시 SHA를 다시 조회하고 한 번 재시도할 수 있다.")
  @Test
  void updateConflictRefetchesShaBeforeOneRetry() {
    expectFile("old");
    server
        .expect(requestTo(url))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(jsonPath("$.sha").value("old"))
        .andExpect(jsonPath("$.message").value("한글 제목-update"))
        .andRespond(withStatus(HttpStatus.CONFLICT));
    expectFile("new");
    server
        .expect(requestTo(url))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(jsonPath("$.sha").value("new"))
        .andRespond(withSuccess());
    client.sync(content(EventType.UPDATED, markdown, path, List.of()));
    server.verify();
  }

  @DisplayName("이름 변경 시 새 파일을 쓰고 이전 경로를 삭제할 수 있다.")
  @Test
  void renameWritesNewFileAndRemovesOldAndLegacyPaths() {
    String oldPath = "Spring/이전 제목-42.md";
    String oldUrl = url(oldPath);
    String legacyPath = "posts/42.md";
    server.expect(requestTo(url + "?ref=master")).andRespond(withStatus(HttpStatus.NOT_FOUND));
    server
        .expect(requestTo(url))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(jsonPath("$.message").value("한글 제목-update"))
        .andRespond(withStatus(HttpStatus.CREATED));
    server
        .expect(requestTo(oldUrl + "?ref=master"))
        .andRespond(
            withSuccess(
                "{\"sha\":\"old\",\"encoding\":\"base64\",\"content\":\"b2xk\"}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(oldUrl))
        .andExpect(method(HttpMethod.DELETE))
        .andExpect(jsonPath("$.message").value("한글 제목-update"))
        .andExpect(jsonPath("$.sha").value("old"))
        .andRespond(withSuccess());
    server
        .expect(requestTo(url(legacyPath) + "?ref=master"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    client.sync(content(EventType.UPDATED, markdown, path, List.of(oldPath, legacyPath)));
    server.verify();
  }

  @DisplayName("비공개·삭제 글의 내용을 올리지 않고 GitHub 파일을 제거할 수 있다.")
  @Test
  void privateOrDeletedPostRemovesCurrentFileWithoutUploadingContent() {
    expectFile("old");
    server
        .expect(requestTo(url))
        .andExpect(method(HttpMethod.DELETE))
        .andExpect(jsonPath("$.sha").value("old"))
        .andExpect(jsonPath("$.message").value("한글 제목-delete"))
        .andExpect(jsonPath("$.content").doesNotExist())
        .andRespond(withSuccess());
    client.sync(content(EventType.DELETED, null, null, List.of(path)));
    server.verify();
  }

  @DisplayName("Markdown 내용이 같으면 새 커밋 생성을 건너뛸 수 있다.")
  @Test
  void unchangedMarkdownDoesNotCreateCommit() {
    server
        .expect(requestTo(url + "?ref=master"))
        .andRespond(
            withSuccess(
                "{\"sha\":\"same\",\"encoding\":\"base64\",\"content\":\"" + encoded() + "\\n\"}",
                MediaType.APPLICATION_JSON));
    client.sync(content(EventType.UPDATED, markdown, path, List.of()));
    server.verify();
  }

  @DisplayName("없는 비공개 글의 GitHub 파일 생성을 건너뛸 수 있다.")
  @Test
  void missingPrivatePostDoesNotCreateFile() {
    server.expect(requestTo(url + "?ref=master")).andRespond(withStatus(HttpStatus.NOT_FOUND));
    client.sync(content(EventType.UPDATED, null, null, List.of(path)));
    server.verify();
  }

  @DisplayName("GitHub 오류 응답이나 토큰의 외부 노출을 차단할 수 있다.")
  @Test
  void upstreamErrorDoesNotLeakResponseOrToken() {
    server
        .expect(anything())
        .andRespond(withStatus(HttpStatus.FORBIDDEN).body("private-upstream-data"));
    assertThatThrownBy(() -> client.sync(content(EventType.CREATED, markdown, path, List.of())))
        .isInstanceOfSatisfying(
            RestApiException.class,
            error -> {
              assertThat(error.getCause()).isNull();
              assertThat(error.getMessage())
                  .doesNotContain("private-upstream-data", "installation-token");
            });
    server.verify();
  }

  private String encoded() {
    return Base64.getEncoder().encodeToString(markdown.getBytes(StandardCharsets.UTF_8));
  }

  private GitHubBlogSyncContent content(
      EventType eventType, String body, String currentPath, List<String> obsolete) {
    return new GitHubBlogSyncContent(connection, "한글 제목", eventType, body, currentPath, obsolete);
  }

  private String url(String filePath) {
    String[] parts = filePath.split("/", 2);
    return "https://api.github.com/repositories/20/contents/"
        + UriUtils.encodePathSegment(parts[0], StandardCharsets.UTF_8)
        + "/"
        + UriUtils.encodePathSegment(parts[1], StandardCharsets.UTF_8);
  }

  private void expectFile(String sha) {
    server
        .expect(requestTo(url + "?ref=master"))
        .andRespond(
            withSuccess(
                "{\"sha\":\"" + sha + "\",\"encoding\":\"base64\",\"content\":\"b2xk\"}",
                MediaType.APPLICATION_JSON));
  }
}
