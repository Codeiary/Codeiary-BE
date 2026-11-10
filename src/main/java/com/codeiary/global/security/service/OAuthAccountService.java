package com.codeiary.global.security.service;

import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.exception.UserErrorCode;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.logging.LogExecution;
import com.codeiary.domain.auth.dto.AuthInfo;
import com.codeiary.domain.auth.dto.TokenPair;
import com.codeiary.domain.auth.service.TokenSessionService;
import com.codeiary.domain.github.dto.GitHubLinkContext;
import com.codeiary.domain.github.service.GitHubConnectionService;
import com.codeiary.domain.github.service.GitHubRepositoryService;
import com.codeiary.domain.user.entity.enums.OAuthProvider;
import com.codeiary.global.security.dto.oauth2user.CustomOAuth2User;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import com.codeiary.global.security.exception.SecurityErrorCode;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OAuthAccountService {

    private final UserRepository users;
    private final TokenSessionService sessions;
    private final GitHubConnectionService githubConnections;
    private final GitHubRepositoryService githubRepositories;
    private final PlatformTransactionManager transactions;

    @LogExecution
    public Optional<TokenPair> complete(Authentication authentication, OAuth2AuthorizationRequest authorization) {
        if (!(authentication.getPrincipal() instanceof CustomOAuth2User user)) {
            throw new RestApiException(SecurityErrorCode.UNAUTHORIZED);
        }
        var profile = user.getAuthInfo();
        if (profile.provider() == OAuthProvider.GOOGLE) {
            return new TransactionTemplate(transactions).execute(status ->
                    Optional.of(sessions.createSession(getOrCreate(profile).getEmail())));
        }
        if (profile.provider() == OAuthProvider.GITHUB) {
            GitHubLinkContext context = authorization == null ? null
                    : authorization.getAttribute(GitHubLinkContext.ATTRIBUTE);
            githubConnections.connect(profile, context);
            githubRepositories.prepare(context.userId());
            return Optional.empty();
        }
        throw new RestApiException(SecurityErrorCode.UNAUTHORIZED);
    }

    private User getOrCreate(AuthInfo profile) {
        if (profile.provider() == null || !profile.emailVerified()) {
            throw new RestApiException(SecurityErrorCode.UNAUTHORIZED);
        }
        validateText(profile.socialSubject(), 255);
        validateText(profile.socialEmail(), 254);
        validateText(profile.socialName(), 100);

        Optional<User> linkedUser = users.findByOauthProviderAndOauthSubject(
                profile.provider(), profile.socialSubject());
        if (linkedUser.isPresent()) {
            return requireActive(linkedUser.get());
        }

        String email = profile.socialEmail().toLowerCase(Locale.ROOT);
        return users.findByEmailForUpdate(email)
                .map(user -> linkOAuthAccount(user, profile))
                .orElseGet(() -> saveUser(User.createOAuth(
                        email, profile.socialName(), profile.provider(), profile.socialSubject())));
    }

    private User linkOAuthAccount(User user, AuthInfo profile) {
        requireActive(user);

        if (user.isLinkedTo(profile.provider(), profile.socialSubject())) {
            return user;
        }

        if (!profile.emailAuthoritative()) {
            throw new RestApiException(UserErrorCode.OAUTH_ACCOUNT_CONFLICT);
        }

        user.linkOAuthAccount(profile.provider(), profile.socialSubject());
        return saveUser(user);
    }

    private User saveUser(User user) {
        try {
            return users.saveAndFlush(user);
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
