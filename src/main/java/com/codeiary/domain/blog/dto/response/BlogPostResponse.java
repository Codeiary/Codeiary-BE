package com.codeiary.domain.blog.dto.response;

import java.time.LocalDateTime;

public record BlogPostResponse(
        Long id,
        BlogAuthorResponse author,
        String title,
        String content,
        String category,
        String representativeImageUrl,
        boolean publicPost,
        long viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
