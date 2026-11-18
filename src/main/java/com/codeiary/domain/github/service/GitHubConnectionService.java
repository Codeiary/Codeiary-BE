package com.codeiary.domain.github.service;

import com.codeiary.domain.github.client.GitHubRepositoryClient.Repository;
import com.codeiary.domain.github.dto.GitHubLinkContext;
import com.codeiary.domain.github.dto.response.GitHubConnectionResponse;
import com.codeiary.domain.github.dto.response.GitHubRepositoryResponse;
import com.codeiary.domain.github.entity.GitHubConnection;
import com.codeiary.domain.github.exception.GitHubErrorCode;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import com.codeiary.domain.user.entity.enums.OAuthProvider;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.logging.LogExecution;
import com.codeiary.global.security.dto.AuthInfo;
import com.codeiary.global.security.exception.SecurityErrorCode;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@LogExecution
@RequiredArgsConstructor
public class GitHubConnectionService {
  private static final Duration LINK_TTL = Duration.ofMinutes(10);
  private static final String USER_UNIQUE_CONSTRAINT = "github_connection_user_id_key";
  private static final String GITHUB_USER_UNIQUE_CONSTRAINT =
      "github_connection_github_user_id_key";
  private final UserRepository userRepository;
  private final GitHubConnectionRepository gitHubConnectionRepository;
  private final OAuth2AuthorizedClientService authorizedClients;

  @Transactional(readOnly = true)
  public GitHubConnectionResponse findConnection(Long userId) {
    return gitHubConnectionRepository
        .findByUserId(userId)
        .map(connection ->
            new GitHubConnectionResponse(
                true, connection.getGithubLogin(), GitHubRepositoryResponse.from(connection)))
        .orElseGet(() -> new GitHubConnectionResponse(false, null, null));
  }

  @Transactional
  public void saveRepository(
      Long userId,
      Long connectionId,
      Long githubUserId,
      Long installationId,
      Repository repository) {
    userRepository
        .findByIdForUpdate(userId)
        .filter(user -> user.isEnabled() && user.isOnboardingCompleted())
        .orElseThrow(() -> new RestApiException(SecurityErrorCode.UNAUTHORIZED));
    var connection =
        gitHubConnectionRepository
            .findByUserId(userId)
            .filter(item ->
                item.getId().equals(connectionId) && item.getGithubUserId().equals(githubUserId))
            .orElseThrow(() -> new RestApiException(GitHubErrorCode.INVALID_GITHUB_LINK));
    connection.connectRepository(
        installationId,
        repository.id(),
        repository.owner().login(),
        repository.name(),
        repository.defaultBranch());
  }

  @Transactional
  public void disconnect(Long userId) {
    if (userRepository.findByIdForUpdate(userId).isEmpty()) {
      return;
    }
    gitHubConnectionRepository
        .findByUserId(userId)
        .ifPresent(connection -> {
          authorizedClients.removeAuthorizedClient(
              OAuthProvider.GITHUB.getRegistrationId(), connection.getGithubUserId().toString());
          gitHubConnectionRepository.delete(connection);
        });
  }

  @Transactional
  public void connect(AuthInfo authInfo, GitHubLinkContext gitHubLinkContext) {
    if (gitHubLinkContext == null
        || gitHubLinkContext.createdAt().plus(LINK_TTL).isBefore(Instant.now())) {
      throw new RestApiException(GitHubErrorCode.INVALID_GITHUB_LINK);
    }
    var user =
        userRepository
            .findByIdForUpdate(gitHubLinkContext.userId())
            .filter(candidate -> candidate.isEnabled() && candidate.isOnboardingCompleted())
            .orElseThrow(() -> new RestApiException(SecurityErrorCode.UNAUTHORIZED));
    Long githubUserId = Long.valueOf(authInfo.socialSubject());
    var connection =
        gitHubConnectionRepository
            .findByUserId(user.getId())
            .orElseGet(() -> GitHubConnection.create(user, githubUserId));
    if (!connection.getGithubUserId().equals(githubUserId)) {
      throw new RestApiException(GitHubErrorCode.GITHUB_ACCOUNT_CONFLICT);
    }
    connection.connect(authInfo.socialName());
    try {
      gitHubConnectionRepository.saveAndFlush(connection);
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
