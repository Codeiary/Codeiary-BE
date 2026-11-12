package com.codeiary.domain.github.dto.outbox;

import com.codeiary.domain.github.entity.GitHubConnection;
import com.codeiary.domain.github.entity.enums.EventType;
import java.util.List;

public record GitHubBlogSyncContent(GitHubConnection connection, String title, EventType eventType,
        String markdown, String path, List<String> obsoletePaths) {
}
