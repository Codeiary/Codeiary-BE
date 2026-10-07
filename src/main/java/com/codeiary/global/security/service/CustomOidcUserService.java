package com.codeiary.global.security.service;

import com.codeiary.global.security.dto.AuthInfo;
import com.codeiary.global.security.dto.OAuth2Response;
import com.codeiary.global.security.dto.adapter.OAuth2ResponseFactory;
import com.codeiary.global.security.dto.oauth2user.CustomOidcUser;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class CustomOidcUserService extends OidcUserService {

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser user = super.loadUser(request);
        OAuth2Response profile = OAuth2ResponseFactory.from(
                request.getClientRegistration().getRegistrationId(), user.getAttributes());
        AuthInfo authInfo = new AuthInfo(
                profile.getProvider(), profile.getSubject(), profile.getEmail(), profile.getName());
        return new CustomOidcUser(user, authInfo);
    }
}
