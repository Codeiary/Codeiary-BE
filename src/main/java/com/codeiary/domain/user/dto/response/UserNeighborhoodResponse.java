package com.codeiary.domain.user.dto.response;

public record UserNeighborhoodResponse(
        Long id,
        String nickname,
        String profileImageUrl,
        String githubUrl,
        String contactEmail,
        long postCount
) {
}
