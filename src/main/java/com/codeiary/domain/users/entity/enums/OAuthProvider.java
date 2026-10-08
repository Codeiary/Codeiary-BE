package com.codeiary.domain.users.entity.enums;

import jakarta.persistence.EnumeratedValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OAuthProvider {
    GOOGLE("google");

    @EnumeratedValue
    private final String registrationId;
}
