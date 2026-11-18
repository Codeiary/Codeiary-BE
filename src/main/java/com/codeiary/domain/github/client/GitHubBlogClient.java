package com.codeiary.domain.github.client;

import com.codeiary.domain.github.dto.outbox.GitHubBlogSyncContent;
import com.codeiary.domain.github.entity.GitHubConnection;
import com.codeiary.domain.github.exception.GitHubErrorCode;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.logging.LogExecution;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@LogExecution
@Profile("!loadtest")
public class GitHubBlogClient implements GitHubBlogWriter {
  private final RestClient restClient;
  private final GitHubAppTokenProvider gitHubAppTokenProvider;

  public GitHubBlogClient(
      @Qualifier("githubRestClient") RestClient client, GitHubAppTokenProvider tokens) {
    this.restClient = client;
    this.gitHubAppTokenProvider = tokens;
  }

  @Override
  public void sync(GitHubBlogSyncContent gitHubBlogSyncContent) {
    GitHubConnection gitHubConnection = gitHubBlogSyncContent.connection();
    if (!GitHubRepositoryClient.BLOG_REPOSITORY_NAME.equals(gitHubConnection.getRepositoryName())) {
      throw new RestApiException(GitHubErrorCode.GITHUB_REPOSITORY_UNAVAILABLE);
    }
    try {
      String token =
          gitHubAppTokenProvider.token(
              gitHubConnection.getInstallationId(), gitHubConnection.getRepositoryId());
      for (int attempt = 0; attempt < 2; attempt++) {
        try {
          write(gitHubBlogSyncContent, token);
          return;
        } catch (RestClientResponseException exception) {
          if (attempt == 0 && exception.getStatusCode().value() == 409) continue;
          throw exception;
        }
      }
    } catch (RestClientException exception) {
      throw new RestApiException(GitHubErrorCode.GITHUB_UNAVAILABLE);
    }
  }

  private void write(GitHubBlogSyncContent gitHubBlogSyncContent, String token) {
    String action =
        switch (gitHubBlogSyncContent.eventType()) {
          case CREATED -> "create";
          case UPDATED -> "update";
          case DELETED -> "delete";
        };
    String message =
        gitHubBlogSyncContent.title().replaceAll("[\\r\\n]+", " ").strip() + "-" + action;
    if (gitHubBlogSyncContent.path() != null) {
      writeFile(
          gitHubBlogSyncContent.connection(),
          gitHubBlogSyncContent.path(),
          gitHubBlogSyncContent.markdown(),
          message,
          token);
    }
    for (String path : gitHubBlogSyncContent.obsoletePaths()) {
      writeFile(gitHubBlogSyncContent.connection(), path, null, message, token);
    }
  }

  private void writeFile(
      GitHubConnection gitHubConnection,
      String filePath,
      String markdown,
      String message,
      String token) {
    String[] segments = filePath.split("/", 2);
    String path = "/repositories/{repositoryId}/contents/{category}/{filename}";
    var existing =
        restClient
            .get()
            .uri(
                path + "?ref={branch}",
                gitHubConnection.getRepositoryId(),
                segments[0],
                segments[1],
                gitHubConnection.getBranch())
            .headers(headers -> headers.setBearerAuth(token))
            .exchange((request, response) -> {
              if (response.getStatusCode().value() == 404) return null;
              if (response.getStatusCode().isError())
                throw new RestApiException(GitHubErrorCode.GITHUB_UNAVAILABLE);
              return response.bodyTo(FileContent.class);
            });
    if (markdown == null && existing == null) return;
    String encoded =
        markdown == null
            ? null
            : Base64.getEncoder().encodeToString(markdown.getBytes(StandardCharsets.UTF_8));
    if (existing != null
        && existing.content() != null
        && encoded != null
        && "base64".equals(existing.encoding())
        && encoded.equals(existing.content().replaceAll("\\s", ""))) return;
    var body = new HashMap<String, Object>();
    body.put("message", message);
    body.put("branch", gitHubConnection.getBranch());
    if (existing != null) body.put("sha", existing.sha());
    if (encoded != null) body.put("content", encoded);
    restClient
        .method(markdown == null ? HttpMethod.DELETE : HttpMethod.PUT)
        .uri(path, gitHubConnection.getRepositoryId(), segments[0], segments[1])
        .headers(headers -> headers.setBearerAuth(token))
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FileContent(String sha, String encoding, String content) {}
}
