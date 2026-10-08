package com.codeiary.global.security.dto.adapter;

import com.codeiary.domain.users.entity.enums.OAuthProvider;
import com.codeiary.global.security.dto.OAuth2Response;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

public final class GoogleOAuth2Response implements OAuth2Response {

    private final Map<String, Object> attributes;

    public GoogleOAuth2Response(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public OAuthProvider getProvider() {
        return OAuthProvider.GOOGLE;
    }

    @Override
    public String getSubject() {
        return Objects.toString(attributes.get("sub"), null);
    }

    @Override
    public String getEmail() {
        return Objects.toString(attributes.get("email"), null);
    }

    @Override
    public String getName() {
        return Objects.toString(attributes.get("name"), null);
    }

    @Override
    public boolean isEmailVerified() {
        return Boolean.TRUE.equals(attributes.get("email_verified"));
    }

    @Override
    public boolean isEmailAuthoritative() {
        String email = getEmail();
        Object hostedDomain = attributes.get("hd");
        return isEmailVerified() && StringUtils.hasText(email)
                && (email.toLowerCase(Locale.ROOT).endsWith("@gmail.com")
                || hostedDomain instanceof String domain && StringUtils.hasText(domain));
    }
}
