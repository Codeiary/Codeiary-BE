package com.codeiary.domain.github.service;

import com.codeiary.global.logging.LogExecution;
import com.codeiary.domain.auth.dto.AuthInfo;
import com.codeiary.domain.github.dto.GitHubLinkContext;
import com.codeiary.domain.github.dto.response.GitHubConnectionResponse;
import com.codeiary.domain.github.entity.GitHubConnection;
import com.codeiary.domain.github.exception.GitHubErrorCode;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.domain.user.entity.enums.OAuthProvider;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.exception.SecurityErrorCode;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.transaction.annotation.Transactional;

@Service
@LogExecution
@RequiredArgsConstructor
public class GitHubConnectionService {
    private static final Duration LINK_TTL = Duration.ofMinutes(10);
    private static final String USER_UNIQUE_CONSTRAINT = "github_connection_user_id_key";
    private static final String GITHUB_USER_UNIQUE_CONSTRAINT = "github_connection_github_user_id_key";
    private final UserRepository users;
    private final GitHubConnectionRepository connections;
    private final OAuth2AuthorizedClientService authorizedClients;

    @Transactional(readOnly = true)
    public GitHubConnectionResponse findConnection(Long userId) {
        return connections.findByUserId(userId)
                .map(connection -> new GitHubConnectionResponse(true, connection.getGithubLogin()))
                .orElseGet(() -> new GitHubConnectionResponse(false, null));
    }

    @Transactional
    public void disconnect(Long userId) {
        if (users.findByIdForUpdate(userId).isEmpty()) {
            return;
        }
        connections.findByUserId(userId).ifPresent(connection -> {
            authorizedClients.removeAuthorizedClient(
                    OAuthProvider.GITHUB.getRegistrationId(), connection.getGithubUserId().toString());
            connections.delete(connection);
        });
    }

    @Transactional
    public void connect(AuthInfo profile, GitHubLinkContext context) {
        if (context == null || context.createdAt().plus(LINK_TTL).isBefore(Instant.now())) {
            throw new RestApiException(GitHubErrorCode.INVALID_GITHUB_LINK);
        }
        var user = users.findByIdForUpdate(context.userId())
                .filter(candidate -> candidate.isEnabled() && candidate.isOnboardingCompleted())
                .orElseThrow(() -> new RestApiException(SecurityErrorCode.UNAUTHORIZED));
        Long githubUserId = Long.valueOf(profile.socialSubject());
        var connection = connections.findByUserId(user.getId())
                .orElseGet(() -> GitHubConnection.create(user, githubUserId));
        if (!connection.getGithubUserId().equals(githubUserId)) {
            throw new RestApiException(GitHubErrorCode.GITHUB_ACCOUNT_CONFLICT);
        }
        connection.connect(profile.socialName());
        try {
            connections.saveAndFlush(connection);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && (USER_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())
                        || GITHUB_USER_UNIQUE_CONSTRAINT.equals(violation.getConstraintName()))) {
                    throw new RestApiException(GitHubErrorCode.GITHUB_ACCOUNT_CONFLICT, exception);
                }
            }
            throw exception;
        }
    }
}
