package com.codeiary.global.security.dto.adapter;

import java.util.Map;
import com.codeiary.domain.user.entity.enums.OAuthProvider;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.codeiary.global.security.dto.OAuth2Response;

public final class OAuth2ResponseFactory {

    private OAuth2ResponseFactory() {
    }

    public static OAuth2Response from(
            String registrationId,
            OAuth2User user
    ) {
        return from(registrationId, user.getAttributes());
    }

    public static OAuth2Response from(
            String registrationId,
            Map<String, Object> attributes
    ) {
        if (OAuthProvider.GOOGLE.getRegistrationId().equalsIgnoreCase(registrationId)) {
            return new GoogleOAuth2Response(attributes);
        }
        throw new OAuth2AuthenticationException(new OAuth2Error("unsupported_provider"));
    }
}
