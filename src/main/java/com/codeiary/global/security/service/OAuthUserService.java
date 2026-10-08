package com.codeiary.global.security.service;

import com.codeiary.domain.users.entity.User;
import com.codeiary.domain.users.exception.UserErrorCode;
import com.codeiary.domain.users.repository.UserRepository;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.dto.AuthInfo;
import com.codeiary.global.security.dto.oauth2user.CustomOAuth2User;
import com.codeiary.global.security.exception.SecurityErrorCode;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OAuthUserService {

    private final UserRepository users;

    @Transactional
    public User getOrCreate(CustomOAuth2User oauthUser) {
        AuthInfo profile = oauthUser.getAuthInfo();
        if (!"google".equals(profile.provider())
                || !Boolean.TRUE.equals(oauthUser.getAttributes().get("email_verified"))
                || !StringUtils.hasText(profile.socialSubject()) || profile.socialSubject().length() > 255
                || !StringUtils.hasText(profile.socialEmail()) || profile.socialEmail().length() > 254
                || !StringUtils.hasText(profile.socialName()) || profile.socialName().length() > 100) {
            throw new RestApiException(SecurityErrorCode.UNAUTHORIZED);
        }

        Optional<User> linkedUser = users.findByOauthProviderAndOauthSubject(
                profile.provider(), profile.socialSubject());
        if (linkedUser.isPresent()) {
            return requireActive(linkedUser.get());
        }

        String email = profile.socialEmail().toLowerCase(Locale.ROOT);
        User user = users.findByEmailForUpdate(email).orElse(null);
        if (user == null) {
            user = new User(email, profile.socialName(), profile.provider(), profile.socialSubject());
        } else {
            requireActive(user);
            if (user.getOauthProvider() != null || user.getOauthSubject() != null) {
                if (profile.provider().equals(user.getOauthProvider())
                        && profile.socialSubject().equals(user.getOauthSubject())) {
                    return user;
                }
                throw new RestApiException(UserErrorCode.OAUTH_ACCOUNT_CONFLICT);
            }
            Object hostedDomain = oauthUser.getAttributes().get("hd");
            boolean authoritativeEmail = email.endsWith("@gmail.com")
                    || hostedDomain instanceof String domain && StringUtils.hasText(domain);
            if (!authoritativeEmail) {
                throw new RestApiException(UserErrorCode.OAUTH_ACCOUNT_CONFLICT);
            }
            user.linkOAuthAccount(profile.provider(), profile.socialSubject());
        }

        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new RestApiException(UserErrorCode.OAUTH_REGISTRATION_CONFLICT, exception);
        }
    }

    private User requireActive(User user) {
        if (!user.isEnabled()) {
            throw new RestApiException(SecurityErrorCode.UNAUTHORIZED);
        }
        return user;
    }
}
