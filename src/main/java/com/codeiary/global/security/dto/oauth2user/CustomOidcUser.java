package com.codeiary.global.security.dto.oauth2user;

import java.util.Map;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.codeiary.domain.auth.dto.AuthInfo;



public final class CustomOidcUser extends CustomOAuth2User implements OidcUser {

	private final OidcUser oidcDelegate;

    public CustomOidcUser(
        OidcUser delegate,
            AuthInfo authInfo
    ) {
        super(delegate, authInfo);
        this.oidcDelegate = delegate;
    }

    @Override
    public Map<String, Object> getClaims() {
        return oidcDelegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return oidcDelegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return oidcDelegate.getIdToken();
    }
}
