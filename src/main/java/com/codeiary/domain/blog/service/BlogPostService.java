package com.codeiary.domain.blog.service;

import com.codeiary.domain.blog.dto.BlogPostMapper;
import com.codeiary.domain.blog.dto.request.BlogPostRequest;
import com.codeiary.domain.blog.dto.response.BlogPostResponse;
import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.exception.BlogErrorCode;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.image.validation.ImageUrlValidator;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.exception.RestApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlogPostService {

    private final BlogPostRepository posts;
    private final BlogPostMapper postMapper;
    private final ImageUrlValidator imageUrls;

    @Transactional
    public BlogPostResponse create(User user, BlogPostRequest request) {
        BlogPostRequest normalized = normalize(request);
        return postMapper.toResponse(posts.save(postMapper.toEntity(normalized, user)));
    }

    public BlogPostResponse get(Long postId, User user) {
        BlogPost post = findPost(postId);
        if (!post.isPublicPost() && !post.isWrittenBy(user)) {
            throw new RestApiException(BlogErrorCode.POST_NOT_FOUND);
        }
        return postMapper.toResponse(post);
    }

    @Transactional
    public BlogPostResponse update(Long postId, User user, BlogPostRequest request) {
        BlogPost post = findOwnedPost(postId, user);
        BlogPostRequest normalized = normalize(request);
        post.update(normalized.title(), normalized.content(), normalized.category(),
                normalized.representativeImageUrl(), normalized.publicPost());
        posts.flush();
        return postMapper.toResponse(post);
    }

    @Transactional
    public void delete(Long postId, User user) {
        posts.delete(findOwnedPost(postId, user));
    }

    private BlogPost findPost(Long postId) {
        return posts.findById(postId)
                .orElseThrow(() -> new RestApiException(BlogErrorCode.POST_NOT_FOUND));
    }

    private BlogPost findOwnedPost(Long postId, User user) {
        BlogPost post = findPost(postId);
        if (!post.isWrittenBy(user)) {
            throw new RestApiException(post.isPublicPost()
                    ? BlogErrorCode.POST_ACCESS_DENIED : BlogErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private BlogPostRequest normalize(BlogPostRequest request) {
        String imageUrl = optionalValue(request.representativeImageUrl());
        if (imageUrl != null) {
            imageUrls.validate(imageUrl);
        }
        return new BlogPostRequest(request.title().trim(), request.content(),
                optionalValue(request.category()), imageUrl, request.publicPost());
    }

    private String optionalValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
