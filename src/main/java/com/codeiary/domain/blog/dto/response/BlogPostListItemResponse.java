package com.codeiary.domain.blog.dto.response;

import java.time.LocalDateTime;

public record BlogPostListItemResponse(
        Long id,
        BlogAuthorResponse author,
        String title,
        String category,
        String representativeImageUrl,
        boolean publicPost,
        long viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
