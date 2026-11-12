package com.codeiary.domain.github.dto;

import java.time.Instant;

public record GitHubLinkContext(Long userId, Instant createdAt) {
    public static final String ATTRIBUTE = "github.link.context";
}
