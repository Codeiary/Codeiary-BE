package com.codeiary.domain.github.client;

import com.codeiary.domain.github.entity.GitHubConnection;

public interface GitHubBlogWriter {
    void sync(GitHubConnection connection, Long postId, String markdown);
}
