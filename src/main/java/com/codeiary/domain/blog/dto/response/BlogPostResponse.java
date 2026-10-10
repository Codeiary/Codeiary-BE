package com.codeiary.domain.blog.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record BlogPostResponse(
        Long id,
        BlogAuthorResponse author,
        String title,
        String content,
        String category,
        List<String> tags,
        String representativeImageUrl,
        boolean publicPost,
        long likeCount,
        boolean likedByMe,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public BlogPostResponse withLikes(long count, boolean liked) {
        return new BlogPostResponse(id, author, title, content, category, tags, representativeImageUrl,
                publicPost, count, liked, createdAt, updatedAt);
    }
}
