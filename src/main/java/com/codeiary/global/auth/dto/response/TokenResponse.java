package com.codeiary.global.auth.dto.response;

import com.codeiary.domain.users.dto.response.UserProfileResponse;

public record TokenResponse(String accessToken, String refreshToken, String tokenType,
                            long expiresIn, long refreshExpiresIn, UserProfileResponse user) {
}
