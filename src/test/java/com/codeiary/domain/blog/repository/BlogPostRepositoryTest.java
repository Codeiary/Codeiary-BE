package com.codeiary.domain.blog.repository;

import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.fixture.BlogPostFixture;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.auditing.CurrentDateTimeProvider;

import static org.assertj.core.api.Assertions.assertThat;

class BlogPostRepositoryTest extends RepositoryTestSupport {

    @Autowired private BlogPostRepository posts;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;
    @Autowired private AuditingHandler auditingHandler;

    @AfterEach
    void restoreClock() {
        auditingHandler.setDateTimeProvider(CurrentDateTimeProvider.INSTANCE);
    }

    @Test
    @DisplayName("테이블에 게시글을 저장하고 작성자와 생성·수정 시간을 조회할 수 있다.")
    void persistAndUpdatePost() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 10, 9, 12, 0);
        auditingHandler.setDateTimeProvider(() -> Optional.of(now));
        var author = users.saveAndFlush(UserFixture.create());
        author.updateProfile("기록자", null);
        Long id = posts.saveAndFlush(BlogPostFixture.create(author, true)).getId();
        entityManager.clear();

        // when
        auditingHandler.setDateTimeProvider(() -> Optional.of(now.plusMinutes(5)));
        BlogPost post = posts.findById(id).orElseThrow();
        assertThat(post.getContent()).isEqualTo(BlogPostFixture.CONTENT);
        post.update("수정 제목", "수정 본문", null, "https://img.example.com/cover.jpg", false);
        posts.flush();
        entityManager.clear();
        BlogPost saved = posts.findById(id).orElseThrow();
        entityManager.detach(saved);

        // then
        assertThat(saved.getTitle()).isEqualTo("수정 제목");
        assertThat(saved.getContent()).isEqualTo("수정 본문");
        assertThat(saved.getCategory()).isNull();
        assertThat(saved.isPublicPost()).isFalse();
        assertThat(saved.getViewCount()).isZero();
        assertThat(saved.getRepresentativeImageUrl()).isEqualTo("https://img.example.com/cover.jpg");
        assertThat(saved.getAuthor().getNickname()).isEqualTo("기록자");
        assertThat(saved.getCreatedAt()).isEqualTo(now);
        assertThat(saved.getUpdatedAt()).isEqualTo(now.plusMinutes(5));
    }
}
