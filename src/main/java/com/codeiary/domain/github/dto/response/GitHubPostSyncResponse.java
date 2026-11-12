package com.codeiary.domain.github.dto.response;

import com.codeiary.domain.github.entity.enums.OutBoxStatus;

public record GitHubPostSyncResponse(boolean connected, OutBoxStatus status) {
}
