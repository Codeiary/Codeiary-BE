package com.codeiary.domain.blog.dto.response;

import java.util.List;

public record BlogPostPageResponse(
        List<BlogPostListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
