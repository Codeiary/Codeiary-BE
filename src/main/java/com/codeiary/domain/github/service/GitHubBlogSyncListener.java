package com.codeiary.domain.github.service;

import com.codeiary.domain.blog.event.BlogPostChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GitHubBlogSyncListener {
    private final GitHubBlogSyncService sync;

    @EventListener
    public void onChanged(BlogPostChanged event) {
        sync.sync(event);
    }
}
