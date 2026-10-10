package com.codeiary.domain.blog.service;

import com.codeiary.domain.blog.dto.BlogPostMapper;
import com.codeiary.domain.blog.dto.request.BlogPostRequest;
import com.codeiary.domain.blog.dto.response.BlogPostResponse;
import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.entity.Category;
import com.codeiary.domain.blog.entity.Tag;
import com.codeiary.domain.blog.exception.BlogErrorCode;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.blog.repository.PostLikeRepository;
import com.codeiary.domain.blog.repository.CategoryRepository;
import com.codeiary.domain.blog.repository.TagRepository;
import com.codeiary.domain.image.validation.ImageUrlValidator;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.exception.RestApiException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlogPostService {

    private final BlogPostRepository posts;
    private final PostLikeRepository postLikes;
    private final BlogPostMapper postMapper;
    private final ImageUrlValidator imageUrls;
    private final CategoryRepository categories;
    private final TagRepository tags;

    @Transactional
    public BlogPostResponse create(User user, BlogPostRequest request) {
        BlogPostRequest normalized = normalize(request);
        Category category = resolveCategory(user, normalized.category());
        BlogPost post = postMapper.toEntity(normalized, user, category);
        post.replaceTags(resolveTags(normalized.tags()));
        return postMapper.toResponse(posts.save(post));
    }

    public BlogPostResponse get(Long postId, User user) {
        BlogPost post = findPost(postId);
        if (!post.isPublicPost() && !post.isWrittenBy(user)) {
            throw new RestApiException(BlogErrorCode.POST_NOT_FOUND);
        }
        boolean likedByMe = user != null && postLikes.existsForUser(postId, user.getId());
        return postMapper.toResponse(post).withLikes(postLikes.countForPost(postId), likedByMe);
    }

    @Transactional
    public BlogPostResponse update(Long postId, User user, BlogPostRequest request) {
        BlogPost post = findOwnedPost(postId, user);
        BlogPostRequest normalized = normalize(request);
        Category category = resolveCategory(user, normalized.category());
        List<Tag> resolvedTags = resolveTags(normalized.tags());
        post.update(normalized.title(), normalized.content(), category,
                normalized.representativeImageUrl(), normalized.publicPost());
        post.replaceTags(resolvedTags);
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
                request.category().trim(), normalizeTags(request.tags()), imageUrl, request.publicPost());
    }

    private Category resolveCategory(User author, String name) {
        categories.insertIfAbsent(author.getId(), name);
        return categories.findByAuthorIdAndName(author.getId(), name);
    }

    private List<Tag> resolveTags(List<String> names) {
        if (names.isEmpty()) {
            return List.of();
        }
        // A consistent insert order also avoids reversed lock acquisition on shared tags.
        names.forEach(tags::insertIfAbsent);
        return tags.findByNameInOrderByNameAsc(names);
    }

    private List<String> normalizeTags(List<String> names) {
        return names == null ? List.of() : names.stream()
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .distinct().sorted().toList();
    }

    private String optionalValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
