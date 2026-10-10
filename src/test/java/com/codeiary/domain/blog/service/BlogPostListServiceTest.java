package com.codeiary.domain.blog.service;

import com.codeiary.domain.blog.dto.BlogPostMapper;
import com.codeiary.domain.blog.dto.request.BlogPostSort;
import com.codeiary.domain.blog.dto.response.BlogAuthorResponse;
import com.codeiary.domain.blog.dto.response.BlogPostListItemResponse;
import com.codeiary.domain.blog.dto.response.BlogPostPageResponse;
import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.fixture.BlogPostFixture;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.blog.repository.PostLikeRepository;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.fixture.UserFixture;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BlogPostListServiceTest {

    @Mock private BlogPostRepository posts;
    @Mock private BlogPostMapper postMapper;
    @Mock private PostLikeRepository postLikes;
    private BlogPostListService service;

    @BeforeEach
    void setUp() {
        service = new BlogPostListService(posts, postMapper, postLikes);
    }

    @Test
    @DisplayName("공개 글을 최신순과 검색 조건으로 페이지 조회할 수 있다.")
    void listPublicPosts() {
        // given
        BlogPost post = BlogPostFixture.createWithId(UserFixture.createWithId(Role.USER), true);
        BlogPostListItemResponse item = item(post);
        given(posts.findPublicPosts(eq("spring"), eq("Java"), eq(null), eq(BlogPostSort.LATEST), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(post), Pageable.ofSize(12), 25));
        given(postMapper.toListItemResponse(post)).willReturn(item);

        // when
        BlogPostPageResponse response = service.getPublicPosts(" spring ", " Java ", null, BlogPostSort.LATEST, 1, 12, null);

        // then
        assertThat(response.content()).containsExactly(item);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(12);
        assertThat(response.totalElements()).isEqualTo(25);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("내 글은 비공개 글을 포함하고 최신순으로 조회할 수 있다.")
    void listMyPosts() {
        // given
        User author = UserFixture.createWithId(Role.USER);
        BlogPost post = BlogPostFixture.createWithId(author, false);
        BlogPostListItemResponse item = item(post);
        given(posts.findAuthorPosts(eq(author.getId()), eq(null), eq(null), eq(null), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(post), Pageable.ofSize(12), 1));
        given(postMapper.toListItemResponse(post)).willReturn(item);

        // when
        BlogPostPageResponse response = service.getMyPosts(author, "", "", null, 0, 100);

        // then
        assertThat(response.content()).containsExactly(item);
        assertThat(response.size()).isEqualTo(12);
        assertThat(response.content().getFirst().publicPost()).isFalse();
    }

    @Test
    @DisplayName("좋아요순 정렬을 선택할 수 있다.")
    void sortByLikes() {
        // given
        given(posts.findPublicPosts(eq(null), eq(null), eq(null), eq(BlogPostSort.LIKES), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(), Pageable.ofSize(12), 0));

        // when
        service.getPublicPosts(null, null, null, BlogPostSort.LIKES, 0, 12, null);

        // then
        org.mockito.BDDMockito.then(posts).should()
                .findPublicPosts(eq(null), eq(null), eq(null), eq(BlogPostSort.LIKES), any(Pageable.class));
    }

    private BlogPostListItemResponse item(BlogPost post) {
        return new BlogPostListItemResponse(post.getId(),
                new BlogAuthorResponse(post.getAuthor().getId(), post.getAuthor().getNickname(), null),
                post.getTitle(), post.getCategory().getName(), List.of(), post.getRepresentativeImageUrl(),
                post.isPublicPost(), 0, false, LocalDateTime.now(), LocalDateTime.now());
    }
}
