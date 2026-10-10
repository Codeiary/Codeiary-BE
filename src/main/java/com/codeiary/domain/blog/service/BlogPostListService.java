package com.codeiary.domain.blog.service;

import com.codeiary.domain.blog.dto.BlogPostMapper;
import com.codeiary.domain.blog.dto.request.BlogPostSort;
import com.codeiary.domain.blog.dto.response.BlogPostListItemResponse;
import com.codeiary.domain.blog.dto.response.BlogPostPageResponse;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.blog.repository.PostLikeRepository;
import com.codeiary.domain.user.entity.User;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlogPostListService {

    private static final int MAX_PAGE_SIZE = 50;

    private final BlogPostRepository posts;
    private final BlogPostMapper postMapper;
    private final PostLikeRepository postLikes;

    public BlogPostPageResponse getPublicPosts(String search, String category, String tag, BlogPostSort sort,
                                               int page, int size, User user) {
        Page<BlogPostListItemResponse> result = posts.findPublicPosts(normalize(search), normalize(category),
                        normalize(tag), sort, pageable(page, size))
                .map(postMapper::toListItemResponse);
        return toResponse(withLikes(result, user == null ? null : user.getId()));
    }

    public BlogPostPageResponse getMyPosts(User user, String search, String category, String tag, int page, int size) {
        Page<BlogPostListItemResponse> result = posts.findAuthorPosts(user.getId(), normalize(search),
                        normalize(category), normalize(tag), pageable(page, size))
                .map(postMapper::toListItemResponse);
        return toResponse(withLikes(result, user.getId()));
    }

    private Page<BlogPostListItemResponse> withLikes(Page<BlogPostListItemResponse> result, Long userId) {
        List<Long> postIds = result.getContent().stream().map(BlogPostListItemResponse::id).toList();
        if (postIds.isEmpty()) {
            return result;
        }
        Map<Long, Long> counts = postLikes.countForPosts(postIds).stream()
                .collect(Collectors.toMap(PostLikeRepository.LikeCount::getPostId,
                        PostLikeRepository.LikeCount::getLikeCount));
        Set<Long> likedIds = userId == null ? Set.of() : postLikes.findLikedPostIds(userId, postIds);
        return result.map(item -> item.withLikes(counts.getOrDefault(item.id(), 0L), likedIds.contains(item.id())));
    }

    private PageRequest pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }

    private BlogPostPageResponse toResponse(Page<BlogPostListItemResponse> result) {
        return new BlogPostPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
