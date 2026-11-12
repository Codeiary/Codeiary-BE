package com.codeiary.domain.github.client;

import com.codeiary.domain.github.dto.outbox.GitHubBlogSyncContent;

public interface GitHubBlogWriter {
    void sync(GitHubBlogSyncContent content);
}
