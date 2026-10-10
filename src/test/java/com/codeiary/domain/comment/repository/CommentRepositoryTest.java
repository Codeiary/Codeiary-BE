package com.codeiary.domain.comment.repository;

import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.entity.Category;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.blog.repository.CategoryRepository;
import com.codeiary.domain.comment.entity.Comment;
import com.codeiary.domain.comment.entity.CommentTargetType;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class CommentRepositoryTest extends RepositoryTestSupport {

    @Autowired private CommentRepository comments;
    @Autowired private BlogPostRepository posts;
    @Autowired private CategoryRepository categories;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("게시글 댓글과 한 단계 답글을 순서대로 저장하고 조회할 수 있다.")
    void persistCommentThread() {
        // given
        User author = users.saveAndFlush(UserFixture.create(Role.USER));
        author.updateProfile("기록자", "https://image.example.com/profile.png");
        Category category = categories.saveAndFlush(Category.create(author, "개발"));
        BlogPost post = posts.saveAndFlush(BlogPost.create(author, "글", "본문", category, null, true));
        Comment root = comments.saveAndFlush(Comment.create(CommentTargetType.BLOG_POST, post.getId(), author,
                null, "댓글 😀"));
        Comment reply = comments.saveAndFlush(Comment.create(CommentTargetType.BLOG_POST, post.getId(), author,
                root, "답글 🎉"));
        entityManager.clear();

        // when
        var result = comments.findAllByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
                CommentTargetType.BLOG_POST, post.getId());

        // then
        assertThat(result).extracting(Comment::getId).containsExactly(root.getId(), reply.getId());
        assertThat(result.getFirst().getAuthor().getNickname()).isEqualTo("기록자");
        assertThat(result.get(1).getParent().getId()).isEqualTo(root.getId());
        assertThat(result).allSatisfy(comment -> {
            assertThat(comment.getTargetType()).isEqualTo(CommentTargetType.BLOG_POST);
            assertThat(comment.getTargetId()).isEqualTo(post.getId());
        });
    }
}
