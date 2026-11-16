package com.codeiary.global.security.dto.oauth2user;

import java.util.Collection;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.codeiary.global.security.dto.AuthInfo;


public class CustomOAuth2User implements OAuth2User {

    protected final OAuth2User delegate;
    private final AuthInfo authInfo;

    public CustomOAuth2User(
            OAuth2User delegate,
            AuthInfo authInfo
    ) {
        this.delegate = delegate;
        this.authInfo = authInfo;
    }


    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return authInfo.socialSubject();
    }

    public String getEmail() {
        return authInfo.socialEmail();
    }

    public AuthInfo getAuthInfo() {
        return authInfo;
    }
}