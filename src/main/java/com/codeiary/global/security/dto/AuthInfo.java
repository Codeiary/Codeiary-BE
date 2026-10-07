package com.codeiary.global.security.dto;

public record AuthInfo(
        String provider,
        String socialSubject,
        String socialEmail,
        String socialName
) {

}