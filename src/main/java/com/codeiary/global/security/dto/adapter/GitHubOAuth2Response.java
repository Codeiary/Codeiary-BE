package com.codeiary.global.security.dto.adapter;

import com.codeiary.domain.user.entity.enums.OAuthProvider;
import com.codeiary.global.security.dto.OAuth2Response;
import java.util.Map;
import java.util.Objects;

public final class GitHubOAuth2Response implements OAuth2Response {

    private final Map<String, Object> attributes;

    public GitHubOAuth2Response(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public OAuthProvider getProvider() {
        return OAuthProvider.GITHUB;
    }

    @Override
    public String getSubject() {
        return Objects.toString(attributes.get("id"), null);
    }

    @Override
    public String getEmail() {
        return Objects.toString(attributes.get("email"), null);
    }

    @Override
    public String getName() {
        return Objects.toString(attributes.get("login"), null);
    }

    @Override
    public boolean isEmailVerified() {
        return false;
    }

    @Override
    public boolean isEmailAuthoritative() {
        return false;
    }
}
