package com.codeiary.domain.comment.service;

import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.fixture.BlogPostFixture;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.comment.dto.CommentMapper;
import com.codeiary.domain.comment.dto.request.CommentCreateRequest;
import com.codeiary.domain.comment.entity.CommentTargetType;
import com.codeiary.domain.comment.exception.CommentErrorCode;
import com.codeiary.domain.comment.repository.CommentRepository;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.global.exception.RestApiException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository comments;
    @Mock private BlogPostRepository posts;
    @Mock private CommentMapper mapper;
    @InjectMocks private CommentService service;

    @Test
    @DisplayName("존재하지 않는 부모 댓글에는 답글을 작성할 수 없다.")
    void rejectMissingReplyParent() {
        // given
        User author = UserFixture.createWithId(Role.USER);
        BlogPost post = BlogPostFixture.createWithId(author, true);
        given(posts.findById(post.getId())).willReturn(Optional.of(post));
        given(comments.findByIdAndTargetTypeAndTargetId(999L, CommentTargetType.BLOG_POST, post.getId()))
                .willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.create(post.getId(), author,
                new CommentCreateRequest("답글", 999L)))
                .isInstanceOfSatisfying(RestApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CommentErrorCode.COMMENT_REPLY_NOT_ALLOWED));
    }
}
