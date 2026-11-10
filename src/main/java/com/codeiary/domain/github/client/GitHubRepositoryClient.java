package com.codeiary.domain.github.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.codeiary.domain.github.exception.GitHubErrorCode;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.logging.LogExecution;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@LogExecution
public class GitHubRepositoryClient {
    public static final int PAGE_SIZE = 100;
    public static final String BLOG_REPOSITORY_NAME = "CodeiaryBlog";
    private final RestClient client;

    public GitHubRepositoryClient(@Qualifier("githubRestClient") RestClient client) {
        this.client = client;
    }

    public Repository ensureBlogRepository(String token, String login) {
        var existing = findRepository(token, login);
        if (existing.isPresent()) return existing.get();
        try {
            return body(client.post().uri("/user/repos").headers(headers -> headers.setBearerAuth(token))
                    .body(Map.of("name", BLOG_REPOSITORY_NAME, "private", true, "auto_init", true,
                            "description", "Codeiary blog posts")), Repository.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 422) {
                return findRepository(token, login)
                        .orElseThrow(() -> new RestApiException(GitHubErrorCode.GITHUB_REPOSITORY_CREATION_FAILED));
            }
            if (exception.getStatusCode().value() == 403) {
                throw new RestApiException(GitHubErrorCode.GITHUB_APP_INSTALLATION_REQUIRED);
            }
            throw translate(exception);
        } catch (RestClientException exception) {
            throw new RestApiException(GitHubErrorCode.GITHUB_UNAVAILABLE);
        }
    }

    private Optional<Repository> findRepository(String token, String login) {
        try {
            return client.get().uri("/repos/{owner}/{name}", login, BLOG_REPOSITORY_NAME)
                    .headers(headers -> headers.setBearerAuth(token)).exchange((request, response) -> {
                        if (response.getStatusCode() == HttpStatus.NOT_FOUND) return Optional.empty();
                        if (response.getStatusCode().isError()) throw translate(response.getStatusCode().value());
                        return Optional.ofNullable(response.bodyTo(Repository.class));
                    });
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (RestClientException exception) {
            throw new RestApiException(GitHubErrorCode.GITHUB_UNAVAILABLE);
        }
    }

    public Installations installations(String token, int page) {
        return get("/user/installations?per_page=" + PAGE_SIZE + "&page=" + page, token, Installations.class);
    }

    public Repositories repositories(String token, long installationId, int page) {
        return get("/user/installations/" + installationId + "/repositories?per_page="
                + PAGE_SIZE + "&page=" + page, token, Repositories.class);
    }

    private <T> T get(String path, String token, Class<T> type) {
        try {
            return body(client.get().uri(path).headers(headers -> headers.setBearerAuth(token)), type);
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (RestClientException exception) {
            throw new RestApiException(GitHubErrorCode.GITHUB_UNAVAILABLE);
        }
    }

    private <T> T body(RestClient.RequestHeadersSpec<?> request, Class<T> type) {
        T result = request.retrieve().body(type);
        if (result == null) throw new RestApiException(GitHubErrorCode.GITHUB_UNAVAILABLE);
        return result;
    }

    private RestApiException translate(RestClientResponseException exception) {
        return translate(exception.getStatusCode().value());
    }

    private RestApiException translate(int status) {
        return new RestApiException(switch (status) {
            case 401 -> GitHubErrorCode.GITHUB_REAUTH_REQUIRED;
            case 403 -> GitHubErrorCode.GITHUB_ACCESS_DENIED;
            case 404 -> GitHubErrorCode.GITHUB_REPOSITORY_UNAVAILABLE;
            case 429 -> GitHubErrorCode.GITHUB_RATE_LIMITED;
            default -> GitHubErrorCode.GITHUB_UNAVAILABLE;
        });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(long id, String login) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Installation(long id, Account account, Map<String, String> permissions,
            @JsonProperty("suspended_at") String suspendedAt) {
        public boolean writable() {
            return suspendedAt == null && permissions != null && "write".equals(permissions.get("contents"));
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repository(long id, String name, Account owner,
            @JsonProperty("default_branch") String defaultBranch,
            boolean archived, boolean disabled, Map<String, Boolean> permissions) {
        public boolean writable() {
            return !archived && !disabled && permissions != null && Boolean.TRUE.equals(permissions.get("push"));
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Installations(@JsonProperty("total_count") long totalCount, List<Installation> installations) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repositories(@JsonProperty("total_count") long totalCount, List<Repository> repositories) {}
}
