package com.codeiary.domain.blog.repository;

import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.entity.BlogPostTag;
import com.codeiary.domain.blog.entity.Category;
import com.codeiary.domain.blog.entity.Tag;
import com.codeiary.domain.blog.dto.request.BlogPostSort;
import com.codeiary.domain.blog.fixture.BlogPostFixture;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.auditing.CurrentDateTimeProvider;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class BlogPostRepositoryTest extends RepositoryTestSupport {

    @Autowired private BlogPostRepository posts;
    @Autowired private PostLikeRepository postLikes;
    @Autowired private CategoryRepository categories;
    @Autowired private TagRepository tags;
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
        BlogPost initial = BlogPostFixture.create(author, true);
        categories.saveAndFlush(initial.getCategory());
        Long id = posts.saveAndFlush(initial).getId();
        entityManager.clear();

        // when
        auditingHandler.setDateTimeProvider(() -> Optional.of(now.plusMinutes(5)));
        BlogPost post = posts.findById(id).orElseThrow();
        assertThat(post.getContent()).isEqualTo(BlogPostFixture.CONTENT);
        post.update("수정 제목", "수정 본문", post.getCategory(), "https://img.example.com/cover.jpg", false);
        posts.flush();
        entityManager.clear();
        BlogPost saved = posts.findById(id).orElseThrow();
        entityManager.detach(saved);

        // then
        assertThat(saved.getTitle()).isEqualTo("수정 제목");
        assertThat(saved.getContent()).isEqualTo("수정 본문");
        assertThat(saved.getCategory().getName()).isEqualTo("Java");
        assertThat(saved.isPublicPost()).isFalse();
        assertThat(saved.getRepresentativeImageUrl()).isEqualTo("https://img.example.com/cover.jpg");
        assertThat(saved.getAuthor().getNickname()).isEqualTo("기록자");
        assertThat(saved.getCreatedAt()).isEqualTo(now);
        assertThat(saved.getUpdatedAt()).isEqualTo(now.plusMinutes(5));
    }

    @Test
    @DisplayName("좋아요가 많은 공개 글을 먼저 조회할 수 있다.")
    void sortByLikes() {
        // given
        var author = users.saveAndFlush(UserFixture.create());
        var reader = users.saveAndFlush(UserFixture.create("reader@example.com"));
        var popular = BlogPostFixture.create(author, true);
        var recent = BlogPostFixture.create(author, true);
        categories.saveAndFlush(popular.getCategory());
        recent.update(recent.getTitle(), recent.getContent(), popular.getCategory(), null, true);
        posts.saveAndFlush(popular);
        posts.saveAndFlush(recent);
        postLikes.addLike(popular.getId(), reader.getId());

        // when
        var page = posts.findPublicPosts(null, null, null, BlogPostSort.LIKES, PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).extracting(BlogPost::getId).containsExactly(popular.getId(), recent.getId());
    }

    @Test
    @DisplayName("태그로 필터링한 페이지를 중복이나 추가 지연 조회 없이 조회할 수 있다.")
    void paginateWithTags() {
        // given
        var author = users.saveAndFlush(UserFixture.create());
        var category = categories.saveAndFlush(Category.create(author, "Java"));
        var java = tags.saveAndFlush(Tag.create("java"));
        var spring = tags.saveAndFlush(Tag.create("spring"));
        for (int index = 0; index < 4; index++) {
            var post = BlogPost.create(author, "기록 " + index, "본문", category, null, index < 3);
            post.replaceTags(List.of(java, spring));
            posts.saveAndFlush(post);
        }
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        try {
            // when
            var page = posts.findPublicPosts("기록", "Java", " JAVA ", BlogPostSort.LATEST, PageRequest.of(0, 2));
            entityManager.clear();

            // then
            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getTotalPages()).isEqualTo(2);
            assertThat(page.getContent()).extracting(BlogPost::getTitle).containsExactly("기록 2", "기록 1");
            assertThat(page.getContent()).allSatisfy(post -> {
                assertThat(post.getAuthor().getEmail()).isEqualTo(author.getEmail());
                assertThat(post.getCategory().getName()).isEqualTo("Java");
                assertThat(post.getPostTags()).extracting(link -> link.getTag().getName())
                        .containsExactlyInAnyOrder("java", "spring");
            });
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
            var byTitle = posts.findPublicPosts("기록", null, null, BlogPostSort.LATEST, PageRequest.of(0, 10));
            var byCategory = posts.findPublicPosts("java", null, null, BlogPostSort.LATEST, PageRequest.of(0, 10));
            var byTag = posts.findPublicPosts("spring", null, null, BlogPostSort.LATEST, PageRequest.of(0, 10));
            var byBody = posts.findPublicPosts("본문", null, null, BlogPostSort.LATEST, PageRequest.of(0, 10));
            assertThat(byTitle.getTotalElements()).isEqualTo(3);
            assertThat(byCategory.getTotalElements()).isEqualTo(3);
            assertThat(byTag.getTotalElements()).isEqualTo(3);
            assertThat(byBody.getTotalElements()).isZero();
            var next = posts.findPublicPosts(null, "Java", "java", BlogPostSort.LATEST, PageRequest.of(1, 2));
            assertThat(next.getContent()).extracting(BlogPost::getTitle).containsExactly("기록 0");
            var own = posts.findAuthorPosts(author.getId(), null, "Java", "spring", PageRequest.of(0, 10));
            assertThat(own.getTotalElements()).isEqualTo(4);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    @DisplayName("글의 태그를 수정하고 글을 삭제해도 다른 글의 카테고리와 태그를 보존할 수 있다.")
    void preserveSharedTags() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 10, 9, 12, 0);
        auditingHandler.setDateTimeProvider(() -> Optional.of(now));
        var author = users.saveAndFlush(UserFixture.create());
        var category = categories.saveAndFlush(Category.create(author, "Java"));
        var java = tags.saveAndFlush(Tag.create("java"));
        var spring = tags.saveAndFlush(Tag.create("spring"));
        var first = BlogPost.create(author, "첫 글", "본문", category, null, true);
        var second = BlogPost.create(author, "다른 글", "본문", category, null, true);
        first.replaceTags(List.of(java, spring));
        second.replaceTags(List.of(java));
        posts.saveAllAndFlush(List.of(first, second));
        Long retainedLink = first.getPostTags().getFirst().getId();

        // when
        auditingHandler.setDateTimeProvider(() -> Optional.of(now.plusMinutes(5)));
        first.replaceTags(List.of(java));
        posts.flush();
        entityManager.clear();

        // then
        first = posts.findById(first.getId()).orElseThrow();
        assertThat(first.getUpdatedAt()).isEqualTo(now.plusMinutes(5));
        assertThat(first.getPostTags()).extracting(BlogPostTag::getId).containsExactly(retainedLink);
        assertThat(tags.count()).isEqualTo(2);
        posts.delete(first);
        posts.flush();
        entityManager.clear();
        var remaining = posts.findById(second.getId()).orElseThrow();
        assertThat(remaining.getCategory().getId()).isEqualTo(category.getId());
        assertThat(remaining.getPostTags()).extracting(link -> link.getTag().getName()).containsExactly("java");
        assertThat(tags.count()).isEqualTo(2);
        assertThat(categories.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("카테고리는 작성자별로 공유하고 태그는 전체 게시글에서 공유할 수 있다.")
    void reuseCategoryAndTag() {
        // given
        var first = users.saveAndFlush(UserFixture.create());
        var second = users.saveAndFlush(UserFixture.create("other@example.com"));

        // when
        categories.insertIfAbsent(first.getId(), "Java");
        categories.insertIfAbsent(first.getId(), "Java");
        categories.insertIfAbsent(second.getId(), "Java");
        tags.insertIfAbsent("java");
        tags.insertIfAbsent("java");

        // then
        assertThat(categories.count()).isEqualTo(2);
        assertThat(categories.findByAuthorIdAndName(first.getId(), "Java").getId())
                .isNotEqualTo(categories.findByAuthorIdAndName(second.getId(), "Java").getId());
        assertThat(tags.findByNameInOrderByNameAsc(List.of("java"))).hasSize(1);
    }
}
