package com.codeiary.domain.blog.service;

import com.codeiary.domain.blog.dto.BlogPostMapper;
import com.codeiary.domain.blog.dto.request.BlogPostRequest;
import com.codeiary.domain.blog.dto.response.BlogPostResponse;
import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.entity.Category;
import com.codeiary.domain.blog.entity.Tag;
import com.codeiary.domain.blog.repository.CategoryRepository;
import com.codeiary.domain.blog.repository.TagRepository;
import java.util.List;
import com.codeiary.domain.blog.exception.BlogErrorCode;
import com.codeiary.domain.blog.fixture.BlogPostFixture;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.blog.repository.PostLikeRepository;
import com.codeiary.domain.image.validation.ImageUrlValidator;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.RestApiException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BlogPostServiceTest {

    @Mock private BlogPostRepository posts;
    @Mock private PostLikeRepository postLikes;
    @Mock private ImageUrlValidator imageUrls;
    @Mock private CategoryRepository categories;
    @Mock private TagRepository tags;
    private BlogPostService service;

    @BeforeEach
    void setUp() {
        service = new BlogPostService(posts, postLikes, Mappers.getMapper(BlogPostMapper.class), imageUrls, categories, tags);
    }

    @Test
    @DisplayName("작성자와 Markdown 원문을 유지하며 게시글을 생성할 수 있다.")
    void createPost() {
        // given
        User user = UserFixture.createWithId(Role.USER);
        user.updateProfile("기록자", "https://img.example.com/avatar.jpg");
        given(categories.findByAuthorIdAndName(user.getId(), "Java"))
                .willReturn(Category.create(user, "Java"));
        given(tags.findByNameInOrderByNameAsc(List.of("java", "spring")))
                .willReturn(List.of(Tag.create("java"), Tag.create("spring")));
        given(posts.save(any(BlogPost.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        BlogPostResponse response = service.create(user, BlogPostFixture.request());

        // then
        assertThat(response.title()).isEqualTo("첫 기록");
        assertThat(response.content()).isEqualTo(BlogPostFixture.CONTENT);
        assertThat(response.category()).isEqualTo("Java");
        assertThat(response.tags()).containsExactly("java", "spring");
        then(tags).should().insertIfAbsent("java");
        then(tags).should().insertIfAbsent("spring");
        assertThat(response.representativeImageUrl()).isNull();
        assertThat(response.author().id()).isEqualTo(user.getId());
        assertThat(response.author().nickname()).isEqualTo("기록자");
        assertThat(response.author().profileImageUrl()).isEqualTo(user.getProfileImageUrl());
        then(imageUrls).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"USER", "ADMIN"})
    @DisplayName("타인의 공개 글 수정과 삭제를 차단할 수 있다.")
    void rejectOtherAuthor(Role role) {
        // given
        BlogPost post = BlogPostFixture.createWithId(UserFixture.createWithId(Role.USER), true);
        User other = UserFixture.createWithId(role);
        ReflectionTestUtils.setField(other, "id", 2L);
        given(posts.findById(BlogPostFixture.ID)).willReturn(Optional.of(post));

        // when / then
        assertThatThrownBy(() -> service.update(BlogPostFixture.ID, other, BlogPostFixture.request()))
                .isInstanceOfSatisfying(RestApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(BlogErrorCode.POST_ACCESS_DENIED));
        assertThatThrownBy(() -> service.delete(BlogPostFixture.ID, other))
                .isInstanceOfSatisfying(RestApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(BlogErrorCode.POST_ACCESS_DENIED));
        then(posts).should(never()).delete(any());
    }

    @Test
    @DisplayName("존재하지 않는 게시글 요청을 구분할 수 있다.")
    void rejectMissingPost() {
        // given
        given(posts.findById(BlogPostFixture.ID)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.get(BlogPostFixture.ID, null))
                .isInstanceOfSatisfying(RestApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(BlogErrorCode.POST_NOT_FOUND));
    }

    @Test
    @DisplayName("잘못된 대표 이미지 주소로 게시글을 변경하지 않을 수 있다.")
    void rejectInvalidImage() {
        // given
        User user = UserFixture.createWithId();
        BlogPost post = BlogPostFixture.createWithId(user, true);
        var request = new BlogPostRequest("수정", "수정 내용", "Java", List.of(), "javascript:alert(1)", false);
        given(posts.findById(BlogPostFixture.ID)).willReturn(Optional.of(post));
        willThrow(new RestApiException(CommonErrorCode.INVALID_PARAMETER))
                .given(imageUrls).validate(request.representativeImageUrl());

        // when / then
        assertThatThrownBy(() -> service.update(BlogPostFixture.ID, user, request))
                .isInstanceOf(RestApiException.class);
        assertThat(post.getTitle()).isEqualTo("첫 기록");
        assertThat(post.isPublicPost()).isTrue();
    }
}
