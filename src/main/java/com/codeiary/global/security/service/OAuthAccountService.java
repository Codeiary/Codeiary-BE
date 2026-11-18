package com.codeiary.global.security.service;

import com.codeiary.domain.auth.dto.TokenPair;
import com.codeiary.domain.auth.service.TokenService;
import com.codeiary.domain.github.dto.GitHubLinkContext;
import com.codeiary.domain.github.service.GitHubConnectionService;
import com.codeiary.domain.github.service.GitHubRepositoryService;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.entity.enums.OAuthProvider;
import com.codeiary.domain.user.exception.UserErrorCode;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.logging.LogExecution;
import com.codeiary.global.security.dto.AuthInfo;
import com.codeiary.global.security.dto.oauth2user.CustomOAuth2User;
import com.codeiary.global.security.exception.SecurityErrorCode;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OAuthAccountService {

  private final UserRepository userRepository;
  private final TokenService tokenService;
  private final GitHubConnectionService gitHubConnectionService;
  private final GitHubRepositoryService gitHubRepositoryService;
  private final PlatformTransactionManager platformTransactionManager;

  @LogExecution
  public Optional<TokenPair> complete(
      Authentication authentication, OAuth2AuthorizationRequest authorization) {
    if (!(authentication.getPrincipal() instanceof CustomOAuth2User user)) {
      throw new RestApiException(SecurityErrorCode.UNAUTHORIZED);
    }
    var profile = user.getAuthInfo();
    if (profile.provider() == OAuthProvider.GOOGLE) {
      return new TransactionTemplate(platformTransactionManager)
          .execute(status -> Optional.of(tokenService.login(getOrCreate(profile).getEmail())));
    }
    if (profile.provider() == OAuthProvider.GITHUB) {
      GitHubLinkContext gitHubLinkContext =
          authorization == null ? null : authorization.getAttribute(GitHubLinkContext.ATTRIBUTE);
      gitHubConnectionService.connect(profile, gitHubLinkContext);
      gitHubRepositoryService.prepare(gitHubLinkContext.userId());
      return Optional.empty();
    }
    throw new RestApiException(SecurityErrorCode.UNAUTHORIZED);
  }

  private User getOrCreate(AuthInfo authInfo) {
    if (authInfo.provider() == null || !authInfo.emailVerified()) {
      throw new RestApiException(SecurityErrorCode.UNAUTHORIZED);
    }
    validateText(authInfo.socialSubject(), 255);
    validateText(authInfo.socialEmail(), 254);
    validateText(authInfo.socialName(), 100);

    Optional<User> linkedUser =
        userRepository.findByOauthProviderAndOauthSubject(
            authInfo.provider(), authInfo.socialSubject());
    if (linkedUser.isPresent()) {
      return requireActive(linkedUser.get());
    }

    String email = authInfo.socialEmail().toLowerCase(Locale.ROOT);
    return userRepository
        .findByEmailForUpdate(email)
        .map(user -> linkOAuthAccount(user, authInfo))
        .orElseGet(() ->
            saveUser(
                User.createOAuth(
                    email, authInfo.socialName(), authInfo.provider(), authInfo.socialSubject())));
  }

  private User linkOAuthAccount(User user, AuthInfo authInfo) {
    requireActive(user);

    if (user.isLinkedTo(authInfo.provider(), authInfo.socialSubject())) {
      return user;
    }

    if (!authInfo.emailAuthoritative()) {
      throw new RestApiException(UserErrorCode.OAUTH_ACCOUNT_CONFLICT);
    }

    user.linkOAuthAccount(authInfo.provider(), authInfo.socialSubject());
    return saveUser(user);
  }

  private User saveUser(User user) {
    try {
      return userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException exception) {
      for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
        if (cause instanceof ConstraintViolationException violation
            && ("users_email_key".equals(violation.getConstraintName())
                || "uk_users_oauth_identity".equals(violation.getConstraintName()))) {
          throw new RestApiException(UserErrorCode.OAUTH_REGISTRATION_CONFLICT, exception);
        }
      }
      throw exception;
    }
  }

  private void validateText(String value, int maxLength) {
    if (!StringUtils.hasText(value) || value.length() > maxLength) {
      throw new RestApiException(SecurityErrorCode.UNAUTHORIZED);
    }
  }

  private User requireActive(User user) {
    if (!user.isEnabled()) {
      throw new RestApiException(SecurityErrorCode.UNAUTHORIZED);
    }
    return user;
  }
}
