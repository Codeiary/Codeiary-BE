package com.codeiary.domain.user.dto.response;

public record PublicUserProfileResponse(
        Long id,
        String nickname,
        String profileImageUrl,
        String githubUrl,
        String contactEmail
) {
}
