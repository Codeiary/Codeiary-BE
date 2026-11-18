package com.codeiary.global.security.service;

import com.codeiary.global.logging.LogExecution;
import com.codeiary.global.security.dto.AuthInfo;
import com.codeiary.global.security.dto.OAuth2Response;
import com.codeiary.global.security.dto.adapter.OAuth2ResponseFactory;
import com.codeiary.global.security.dto.oauth2user.CustomOidcUser;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class CustomOidcUserService extends OidcUserService {

  @Override
  @LogExecution
  public OidcUser loadUser(OidcUserRequest oidcUserRequest) {
    OidcUser oidcUser = super.loadUser(oidcUserRequest);
    OAuth2Response profile =
        OAuth2ResponseFactory.from(
            oidcUserRequest.getClientRegistration().getRegistrationId(), oidcUser.getAttributes());
    AuthInfo authInfo =
        new AuthInfo(
            profile.getProvider(),
            profile.getSubject(),
            profile.getEmail(),
            profile.getName(),
            profile.isEmailVerified(),
            profile.isEmailAuthoritative());
    return new CustomOidcUser(oidcUser, authInfo);
  }
}
