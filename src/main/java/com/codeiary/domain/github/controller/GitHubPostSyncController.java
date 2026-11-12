package com.codeiary.domain.github.controller;

import com.codeiary.domain.github.dto.response.GitHubPostSyncResponse;
import com.codeiary.domain.github.service.GitHubBlogOutboxService;
import com.codeiary.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/github/posts")
public class GitHubPostSyncController {
    private final GitHubBlogOutboxService outbox;

    @GetMapping("/{postId}/sync")
    public ResponseEntity<GitHubPostSyncResponse> status(@PathVariable Long postId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(outbox.statusFor(postId, user.getId()));
    }
}
