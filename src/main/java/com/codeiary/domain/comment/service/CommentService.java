package com.codeiary.domain.comment.service;

import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.comment.dto.CommentMapper;
import com.codeiary.domain.comment.dto.request.CommentCreateRequest;
import com.codeiary.domain.comment.dto.request.CommentUpdateRequest;
import com.codeiary.domain.comment.dto.response.CommentResponse;
import com.codeiary.domain.comment.entity.Comment;
import com.codeiary.domain.comment.entity.CommentTargetType;
import com.codeiary.domain.comment.exception.CommentErrorCode;
import com.codeiary.domain.comment.repository.CommentRepository;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.logging.LogExecution;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

  private final CommentRepository commentRepository;
  private final BlogPostRepository blogPostRepository;
  private final CommentMapper commentMapper;

  @LogExecution
  public List<CommentResponse> getComments(Long postId, User user) {
    findReadablePost(postId, user);
    return commentRepository
        .findAllByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(CommentTargetType.BLOG_POST, postId)
        .stream()
        .map(commentMapper::toResponse)
        .toList();
  }

  @Transactional
  @LogExecution
  public CommentResponse create(Long postId, User user, CommentCreateRequest commentCreateRequest) {
    BlogPost blogPost = findReadablePost(postId, user);
    Comment parent =
        commentCreateRequest.parentId() == null
            ? null
            : findReplyParent(postId, commentCreateRequest.parentId());
    Comment comment =
        Comment.create(
            CommentTargetType.BLOG_POST,
            blogPost.getId(),
            user,
            parent,
            commentCreateRequest.content().trim());
    return commentMapper.toResponse(commentRepository.save(comment));
  }

  @Transactional
  @LogExecution
  public CommentResponse update(
      Long postId, Long commentId, User user, CommentUpdateRequest commentUpdateRequest) {
    Comment comment = findOwnedComment(postId, commentId, user);
    comment.update(commentUpdateRequest.content().trim());
    return commentMapper.toResponse(comment);
  }

  @Transactional
  @LogExecution
  public void delete(Long postId, Long commentId, User user) {
    findOwnedComment(postId, commentId, user).delete();
  }

  private BlogPost findReadablePost(Long postId, User user) {
    BlogPost blogPost =
        blogPostRepository
            .findById(postId)
            .orElseThrow(() -> new RestApiException(CommonErrorCode.RESOURCE_NOT_FOUND));
    if (!blogPost.isPublicPost() && !blogPost.isWrittenBy(user)) {
      throw new RestApiException(CommonErrorCode.RESOURCE_NOT_FOUND);
    }
    return blogPost;
  }

  private Comment findReplyParent(Long postId, Long parentId) {
    Comment comment =
        commentRepository
            .findByIdAndTargetTypeAndTargetId(parentId, CommentTargetType.BLOG_POST, postId)
            .orElseThrow(() -> new RestApiException(CommentErrorCode.COMMENT_REPLY_NOT_ALLOWED));
    if (!comment.isRoot() || comment.isDeleted()) {
      throw new RestApiException(CommentErrorCode.COMMENT_REPLY_NOT_ALLOWED);
    }
    return comment;
  }

  private Comment findOwnedComment(Long postId, Long commentId, User user) {
    Comment comment =
        commentRepository
            .findByIdAndTargetTypeAndTargetId(commentId, CommentTargetType.BLOG_POST, postId)
            .orElseThrow(() -> new RestApiException(CommentErrorCode.COMMENT_NOT_FOUND));
    if (!comment.isWrittenBy(user)) {
      throw new RestApiException(CommentErrorCode.COMMENT_ACCESS_DENIED);
    }
    if (comment.isDeleted()) {
      throw new RestApiException(CommentErrorCode.COMMENT_NOT_FOUND);
    }
    return comment;
  }
}
