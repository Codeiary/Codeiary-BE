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
        long likeCount,
        boolean likedByMe,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public BlogPostListItemResponse withLikes(long count, boolean liked) {
        return new BlogPostListItemResponse(id, author, title, category, tags, representativeImageUrl,
                publicPost, count, liked, createdAt, updatedAt);
    }
}
