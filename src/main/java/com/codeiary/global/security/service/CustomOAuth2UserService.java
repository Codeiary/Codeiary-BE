package com.codeiary.global.security.service;

import com.codeiary.domain.auth.dto.AuthInfo;
import com.codeiary.global.security.dto.OAuth2Response;
import com.codeiary.global.security.dto.adapter.OAuth2ResponseFactory;
import com.codeiary.global.security.dto.oauth2user.CustomOAuth2User;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(request);
        OAuth2Response profile = OAuth2ResponseFactory.from(
                request.getClientRegistration().getRegistrationId(), user);
        AuthInfo authInfo = new AuthInfo(
                profile.getProvider(), profile.getSubject(), profile.getEmail(), profile.getName(),
                profile.isEmailVerified(), profile.isEmailAuthoritative());
        return new CustomOAuth2User(user, authInfo);
    }
}
