package com.codeiary.domain.users.dto.response;

public record PublicUserProfileResponse(
        Long id,
        String nickname,
        String profileImageUrl,
        String githubUrl,
        String contactEmail
) {
}
