package com.codeiary.domain.auth.dto;

import com.codeiary.domain.user.entity.enums.OAuthProvider;

public record AuthInfo(
        OAuthProvider provider,
        String socialSubject,
        String socialEmail,
        String socialName,
        boolean emailVerified,
        boolean emailAuthoritative
) {

}
