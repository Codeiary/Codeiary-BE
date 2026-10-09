package com.codeiary.domain.blog.service;

import com.codeiary.domain.blog.dto.BlogPostMapper;
import com.codeiary.domain.blog.dto.request.BlogPostSort;
import com.codeiary.domain.blog.dto.response.BlogPostListItemResponse;
import com.codeiary.domain.blog.dto.response.BlogPostPageResponse;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.user.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlogPostListService {

    private static final int MAX_PAGE_SIZE = 50;

    private final BlogPostRepository posts;
    private final BlogPostMapper postMapper;

    public BlogPostPageResponse getPublicPosts(String search, String category, BlogPostSort sort,
                                               int page, int size) {
        Page<BlogPostListItemResponse> result = posts.findPublicPosts(normalize(search), normalize(category), sort,
                        pageable(sort, page, size))
                .map(postMapper::toListItemResponse);
        return toResponse(result);
    }

    public BlogPostPageResponse getMyPosts(User user, String search, String category, int page, int size) {
        Page<BlogPostListItemResponse> result = posts.findAuthorPosts(user.getId(), normalize(search),
                        normalize(category), pageable(BlogPostSort.LATEST, page, size))
                .map(postMapper::toListItemResponse);
        return toResponse(result);
    }

    private PageRequest pageable(BlogPostSort sort, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort ordering = sort == BlogPostSort.VIEWS
                ? Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        return PageRequest.of(safePage, safeSize, ordering);
    }

    private BlogPostPageResponse toResponse(Page<BlogPostListItemResponse> result) {
        return new BlogPostPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
