package com.codeiary.domain.github.service;

import com.codeiary.domain.github.client.GitHubRepositoryClient;
import com.codeiary.domain.github.exception.GitHubErrorCode;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.logging.LogExecution;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;

@Service
@LogExecution
@RequiredArgsConstructor
public class GitHubRepositoryService {
    private final GitHubConnectionRepository connections;
    private final GitHubConnectionService connectionService;
    private final OAuth2AuthorizedClientService authorizedClients;
    private final GitHubRepositoryClient client;

    public void prepare(Long userId) {
        var connection = connections.findByUserId(userId)
                .orElseThrow(() -> new RestApiException(GitHubErrorCode.INVALID_GITHUB_LINK));
        OAuth2AuthorizedClient authorized = authorizedClients.loadAuthorizedClient("github", connection.getGithubUserId().toString());
        if (authorized == null || authorized.getAccessToken().getExpiresAt() != null
                && !authorized.getAccessToken().getExpiresAt().isAfter(Instant.now().plusSeconds(30))) {
            throw new RestApiException(GitHubErrorCode.GITHUB_REAUTH_REQUIRED);
        }
        String token = authorized.getAccessToken().getTokenValue();
        Long githubUserId = connection.getGithubUserId();
        String login = connection.getGithubLogin();
        var repository = client.ensureBlogRepository(token, login);
        if (repository.owner().id() != githubUserId
                || !GitHubRepositoryClient.BLOG_REPOSITORY_NAME.equalsIgnoreCase(repository.name())
                || repository.archived() || repository.disabled()) {
            throw new RestApiException(GitHubErrorCode.GITHUB_REPOSITORY_UNAVAILABLE);
        }
        Long installationId = findInstallation(token, githubUserId);
        if (installationId != null && (!repository.writable()
                || !containsRepository(token, installationId, repository.id()))) installationId = null;
        connectionService.saveRepository(userId, connection.getId(), githubUserId,
                installationId, repository);
    }

    private Long findInstallation(String token, long githubUserId) {
        int page = 1;
        do {
            var result = client.installations(token, page);
            for (var installation : result.installations()) {
                if (installation.account().id() == githubUserId && installation.writable()) return installation.id();
            }
            if (!hasNext(page++, result.totalCount())) return null;
        } while (true);
    }

    private boolean containsRepository(String token, long installationId, long repositoryId) {
        int page = 1;
        do {
            var result = client.repositories(token, installationId, page);
            if (result.repositories().stream().anyMatch(item -> item.id() == repositoryId && item.writable())) return true;
            if (!hasNext(page++, result.totalCount())) return false;
        } while (true);
    }

    private static boolean hasNext(int page, long count) {
        return (long) page * GitHubRepositoryClient.PAGE_SIZE < count;
    }
}
