package com.codeiary.global.security.dto.adapter;

import java.util.Map;
import java.util.Locale;

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
        return switch (registrationId.toLowerCase(Locale.ROOT)) {
            case "google" -> new GoogleOAuth2Response(attributes);
            case "naver" -> new NaverOAuth2Response(attributes);
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 OAuth2 provider입니다: " + registrationId
            );
        };
    }
}
