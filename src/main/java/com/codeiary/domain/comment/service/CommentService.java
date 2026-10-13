package com.codeiary.domain.comment.service;

import com.codeiary.domain.comment.dto.CommentMapper;
import com.codeiary.domain.comment.dto.request.CommentCreateRequest;
import com.codeiary.domain.comment.dto.request.CommentUpdateRequest;
import com.codeiary.domain.comment.dto.response.CommentResponse;
import com.codeiary.domain.comment.entity.Comment;
import com.codeiary.domain.comment.entity.CommentTargetType;
import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.comment.exception.CommentErrorCode;
import com.codeiary.domain.comment.repository.CommentRepository;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.exception.CommonErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository comments;
    private final BlogPostRepository posts;
    private final CommentMapper commentMapper;

    public List<CommentResponse> getComments(Long postId, User viewer) {
        findReadablePost(postId, viewer);
        return comments.findAllByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(CommentTargetType.BLOG_POST, postId).stream()
                .map(commentMapper::toResponse)
                .toList();
    }

    @Transactional
    public CommentResponse create(Long postId, User author, CommentCreateRequest request) {
        BlogPost post = findReadablePost(postId, author);
        Comment parent = request.parentId() == null ? null : findReplyParent(postId, request.parentId());
        Comment comment = Comment.create(CommentTargetType.BLOG_POST, post.getId(), author, parent,
                request.content().trim());
        return commentMapper.toResponse(comments.save(comment));
    }

    @Transactional
    public CommentResponse update(Long postId, Long commentId, User author,
                                     CommentUpdateRequest request) {
        Comment comment = findOwnedComment(postId, commentId, author);
        comment.update(request.content().trim());
        return commentMapper.toResponse(comment);
    }

    @Transactional
    public void delete(Long postId, Long commentId, User author) {
        findOwnedComment(postId, commentId, author).delete();
    }

    private BlogPost findReadablePost(Long postId, User viewer) {
        BlogPost post = posts.findById(postId)
                .orElseThrow(() -> new RestApiException(CommonErrorCode.RESOURCE_NOT_FOUND));
        if (!post.isPublicPost() && !post.isWrittenBy(viewer)) {
            throw new RestApiException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        return post;
    }

    private Comment findReplyParent(Long postId, Long parentId) {
        Comment parent = comments.findByIdAndTargetTypeAndTargetId(parentId, CommentTargetType.BLOG_POST, postId)
                .orElseThrow(() -> new RestApiException(CommentErrorCode.COMMENT_REPLY_NOT_ALLOWED));
        if (!parent.isRoot() || parent.isDeleted()) {
            throw new RestApiException(CommentErrorCode.COMMENT_REPLY_NOT_ALLOWED);
        }
        return parent;
    }

    private Comment findOwnedComment(Long postId, Long commentId, User author) {
        Comment comment = comments.findByIdAndTargetTypeAndTargetId(
                        commentId, CommentTargetType.BLOG_POST, postId)
                .orElseThrow(() -> new RestApiException(CommentErrorCode.COMMENT_NOT_FOUND));
        if (!comment.isWrittenBy(author)) {
            throw new RestApiException(CommentErrorCode.COMMENT_ACCESS_DENIED);
        }
        if (comment.isDeleted()) {
            throw new RestApiException(CommentErrorCode.COMMENT_NOT_FOUND);
        }
        return comment;
    }
}
