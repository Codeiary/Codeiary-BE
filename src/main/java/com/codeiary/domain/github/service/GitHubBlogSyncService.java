package com.codeiary.domain.github.service;

import com.codeiary.domain.blog.event.BlogPostChanged;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.github.client.GitHubBlogWriter;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import com.codeiary.global.logging.LogExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@LogExecution
public class GitHubBlogSyncService {
    private final BlogPostRepository posts;
    private final GitHubConnectionRepository connections;
    private final GitHubBlogWriter client;

    public void sync(BlogPostChanged event) {
        var connection = connections.findByUserId(event.authorId()).orElse(null);
        if (connection == null || connection.getInstallationId() == null) return;
        var post = posts.findById(event.postId()).orElse(null);
        String markdown = post != null && post.isPublicPost()
                && post.getAuthor().getId().equals(event.authorId())
                ? "# " + post.getTitle() + "\n\n" + post.getContent() + "\n" : null;
        client.sync(connection, event.postId(), markdown);
    }
}
