package com.codeiary.domain.github.client;

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
    private final RestClient client;
    private final GitHubAppTokenProvider tokens;

    public GitHubBlogClient(@Qualifier("githubRestClient") RestClient client, GitHubAppTokenProvider tokens) {
        this.client = client;
        this.tokens = tokens;
    }

    @Override
    public void sync(GitHubConnection connection, Long postId, String markdown) {
        if (!GitHubRepositoryClient.BLOG_REPOSITORY_NAME.equals(connection.getRepositoryName())) {
            throw new RestApiException(GitHubErrorCode.GITHUB_REPOSITORY_UNAVAILABLE);
        }
        try {
            String token = tokens.token(connection.getInstallationId(), connection.getRepositoryId());
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    write(connection, postId, markdown, token);
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

    private void write(GitHubConnection connection, Long postId, String markdown, String token) {
        String path = "/repositories/" + connection.getRepositoryId() + "/contents/posts/" + postId + ".md";
        var existing = client.get().uri(builder -> builder.path(path).queryParam("ref", connection.getBranch()).build())
                .headers(headers -> headers.setBearerAuth(token)).exchange((request, response) -> {
                    if (response.getStatusCode().value() == 404) return null;
                    if (response.getStatusCode().isError()) throw new RestApiException(GitHubErrorCode.GITHUB_UNAVAILABLE);
                    return response.bodyTo(FileContent.class);
                });
        if (markdown == null && existing == null) return;
        String encoded = markdown == null ? null : Base64.getEncoder().encodeToString(markdown.getBytes(StandardCharsets.UTF_8));
        if (existing != null && existing.content() != null && encoded != null && "base64".equals(existing.encoding())
                && encoded.equals(existing.content().replaceAll("\\s", ""))) return;
        var body = new HashMap<String, Object>();
        body.put("message", (markdown == null ? "Delete" : "Sync") + " blog post " + postId);
        body.put("branch", connection.getBranch());
        if (existing != null) body.put("sha", existing.sha());
        if (encoded != null) body.put("content", encoded);
        client.method(markdown == null ? HttpMethod.DELETE : HttpMethod.PUT)
                .uri(path).headers(headers -> headers.setBearerAuth(token)).body(body).retrieve().toBodilessEntity();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FileContent(String sha, String encoding, String content) {}
}
