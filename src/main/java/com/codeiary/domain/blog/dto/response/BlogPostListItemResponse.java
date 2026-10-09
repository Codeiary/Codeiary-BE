package com.codeiary.domain.blog.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record BlogPostListItemResponse(
        Long id,
        BlogAuthorResponse author,
        String title,
        String category,
        List<String> tags,
        String representativeImageUrl,
        boolean publicPost,
        long viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
