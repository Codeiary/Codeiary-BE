package com.codeiary.domain.blog.event;

public record BlogPostChanged(Long postId, Long authorId) {}
