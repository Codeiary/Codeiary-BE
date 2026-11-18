package com.codeiary.domain.blog.service;

import com.codeiary.domain.blog.dto.BlogPostMapper;
import com.codeiary.domain.blog.dto.request.BlogPostRequest;
import com.codeiary.domain.blog.dto.response.BlogPostResponse;
import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.entity.Category;
import com.codeiary.domain.blog.entity.Tag;
import com.codeiary.domain.blog.exception.BlogErrorCode;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.blog.repository.CategoryRepository;
import com.codeiary.domain.blog.repository.PostLikeRepository;
import com.codeiary.domain.blog.repository.TagRepository;
import com.codeiary.domain.github.entity.enums.EventType;
import com.codeiary.domain.github.service.GitHubBlogOutboxService;
import com.codeiary.domain.github.service.GitHubBlogPath;
import com.codeiary.domain.image.validation.ImageUrlValidator;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.logging.LogExecution;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlogPostService {

  private final BlogPostRepository blogPostRepository;
  private final PostLikeRepository postLikeRepository;
  private final BlogPostMapper blogPostMapper;
  private final ImageUrlValidator imageUrlValidator;
  private final CategoryRepository categoryRepository;
  private final TagRepository tagRepository;
  private final GitHubBlogOutboxService gitHubBlogOutboxService;

  @Transactional
  @LogExecution
  public BlogPostResponse create(User user, BlogPostRequest request) {
    BlogPostRequest normalized = normalize(request);
    Category category = resolveCategory(user, normalized.category());
    BlogPost blogPost = blogPostMapper.toEntity(normalized, user, category);
    blogPost.replaceTags(resolveTags(normalized.tags()));
    blogPostRepository.save(blogPost);
    gitHubBlogOutboxService.savePendingChange(blogPost, EventType.CREATED, null);
    return blogPostMapper.toResponse(blogPost);
  }

  @LogExecution
  public BlogPostResponse get(Long postId, User user) {
    BlogPost blogPost = findPost(postId);
    if (!blogPost.isPublicPost() && !blogPost.isWrittenBy(user)) {
      throw new RestApiException(BlogErrorCode.POST_NOT_FOUND);
    }
    boolean likedByMe = user != null && postLikeRepository.existsForUser(postId, user.getId());
    return blogPostMapper
        .toResponse(blogPost)
        .withLikes(postLikeRepository.countForPost(postId), likedByMe);
  }

  @Transactional
  @LogExecution
  public BlogPostResponse update(Long postId, User user, BlogPostRequest request) {
    BlogPost blogPost = findOwnedPost(postId, user);
    String previousPath = GitHubBlogPath.of(blogPost);
    BlogPostRequest normalized = normalize(request);
    Category category = resolveCategory(user, normalized.category());
    List<Tag> resolvedTags = resolveTags(normalized.tags());
    blogPost.update(
        normalized.title(),
        normalized.content(),
        category,
        normalized.representativeImageUrl(),
        normalized.publicPost());
    blogPost.replaceTags(resolvedTags);
    gitHubBlogOutboxService.savePendingChange(blogPost, EventType.UPDATED, previousPath);
    return blogPostMapper.toResponse(blogPost);
  }

  @Transactional
  @LogExecution
  public void delete(Long postId, User user) {
    BlogPost blogPost = findOwnedPost(postId, user);
    gitHubBlogOutboxService.savePendingChange(
        blogPost, EventType.DELETED, GitHubBlogPath.of(blogPost));
    blogPostRepository.delete(blogPost);
  }

  private BlogPost findPost(Long postId) {
    return blogPostRepository
        .findById(postId)
        .orElseThrow(() -> new RestApiException(BlogErrorCode.POST_NOT_FOUND));
  }

  private BlogPost findOwnedPost(Long postId, User user) {
    BlogPost blogPost = findPost(postId);
    if (!blogPost.isWrittenBy(user)) {
      throw new RestApiException(
          blogPost.isPublicPost()
              ? BlogErrorCode.POST_ACCESS_DENIED
              : BlogErrorCode.POST_NOT_FOUND);
    }
    return blogPost;
  }

  private BlogPostRequest normalize(BlogPostRequest blogPostRequest) {
    String imageUrl = optionalValue(blogPostRequest.representativeImageUrl());
    if (imageUrl != null) {
      imageUrlValidator.validate(imageUrl);
    }
    return new BlogPostRequest(
        blogPostRequest.title().trim(),
        blogPostRequest.content(),
        blogPostRequest.category().trim(),
        normalizeTags(blogPostRequest.tags()),
        imageUrl,
        blogPostRequest.publicPost());
  }

  private Category resolveCategory(User user, String name) {
    categoryRepository.insertIfAbsent(user.getId(), name);
    return categoryRepository.findByAuthorIdAndName(user.getId(), name);
  }

  private List<Tag> resolveTags(List<String> names) {
    if (names.isEmpty()) {
      return List.of();
    }
    names.forEach(tagRepository::insertIfAbsent);
    return tagRepository.findByNameInOrderByNameAsc(names);
  }

  private List<String> normalizeTags(List<String> names) {
    return names == null
        ? List.of()
        : names.stream()
            .map(name -> name.trim().toLowerCase(Locale.ROOT))
            .distinct()
            .sorted()
            .toList();
  }

  private String optionalValue(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
