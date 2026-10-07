package com.codeiary.global.security.token.dto;

import java.time.Instant;

public record TokenPair(
        String accessToken,
        String refreshToken,
        Instant accessExpiresAt,
        Instant refreshExpiresAt
) {
}
