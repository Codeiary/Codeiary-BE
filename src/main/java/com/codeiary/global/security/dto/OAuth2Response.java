package com.codeiary.global.security.dto;

import com.codeiary.domain.user.entity.enums.OAuthProvider;

public interface OAuth2Response {

    OAuthProvider getProvider();

    String getSubject();

    String getEmail();

    String getName();

    boolean isEmailVerified();

    boolean isEmailAuthoritative();
}
