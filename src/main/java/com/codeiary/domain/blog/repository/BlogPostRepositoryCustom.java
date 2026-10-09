package com.codeiary.domain.blog.repository;

import com.codeiary.domain.blog.dto.request.BlogPostSort;
import com.codeiary.domain.blog.entity.BlogPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BlogPostRepositoryCustom {

    Page<BlogPost> findPublicPosts(String search, String category, BlogPostSort sort, Pageable pageable);

    Page<BlogPost> findAuthorPosts(Long authorId, String search, String category, Pageable pageable);
}
