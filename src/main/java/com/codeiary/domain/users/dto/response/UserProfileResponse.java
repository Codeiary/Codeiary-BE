package com.codeiary.domain.users.dto.response;

import com.codeiary.domain.users.entity.enums.Role;

public record UserProfileResponse(
        Long id,
        String email,
        String name,
        String nickname,
        String profileImageUrl,
        boolean onboardingCompleted,
        Role role,
        String githubUrl,
        String contactEmail
) {
}
