package com.codeiary.domain.blog.controller;

import com.codeiary.domain.blog.fixture.BlogPostFixture;
import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.repository.CategoryRepository;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.blog.repository.PostLikeRepository;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.domain.auth.provider.JwtTokenProvider;
import com.codeiary.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class BlogPostControllerTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private BlogPostRepository posts;
    @Autowired private PostLikeRepository postLikes;
    @Autowired private CategoryRepository categories;
    @Autowired private JwtTokenProvider tokens;

    @Test
    @DisplayName("로그인한 작성자가 게시글을 생성하고 수정·삭제할 수 있다.")
    void manageOwnPost() throws Exception {
        // given
        User author = users.saveAndFlush(UserFixture.create(Role.USER));
        Cookie cookie = accessCookie(author);

        // when
        String location = mockMvc.perform(post("/api/posts").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(BlogPostFixture.REQUEST_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.id").value(author.getId()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.category").value("Java"))
                .andExpect(jsonPath("$.tags").value(containsInAnyOrder("java", "spring")))
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.likedByMe").value(false))
                .andReturn().getResponse().getHeader("Location");

        // then
        assertThat(location).startsWith("/api/posts/");
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("첫 기록"))
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.likedByMe").value(false))
                .andExpect(jsonPath("$.tags").value(containsInAnyOrder("java", "spring")))
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andExpect(jsonPath("$.author.role").doesNotExist());
        mockMvc.perform(put(location).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"수정 제목","content":"새 본문","category":"일상","publicPost":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정 제목"))
                .andExpect(jsonPath("$.publicPost").value(false))
                .andExpect(jsonPath("$.tags").isEmpty())
                .andExpect(jsonPath("$.category").value("일상"));
        mockMvc.perform(get(location).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        mockMvc.perform(delete(location).cookie(cookie)).andExpect(status().isNoContent());
        mockMvc.perform(get(location)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("비공개 글을 익명 사용자와 다른 관리자에게 숨길 수 있다.")
    void hidePrivatePost() throws Exception {
        // given
        User author = users.saveAndFlush(UserFixture.create(Role.USER));
        User other = users.saveAndFlush(UserFixture.create("other@example.com"));
        Long id = persistPost(author, false);
        Cookie cookie = accessCookie(other);

        // when / then
        mockMvc.perform(get("/api/posts/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mockMvc.perform(get("/api/posts/{id}", id).cookie(cookie)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/posts/{id}", id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(BlogPostFixture.REQUEST_JSON))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/posts/{id}", id).cookie(cookie)).andExpect(status().isNotFound());
        assertThat(posts.existsById(id)).isTrue();
    }

    @Test
    @DisplayName("로그인 사용자는 게시글에 한 번 좋아요를 누르고 취소할 수 있다.")
    void likePostOnceAndUnlike() throws Exception {
        // given
        User author = users.saveAndFlush(UserFixture.create(Role.USER));
        User reader = users.saveAndFlush(UserFixture.create("reader@example.com"));
        Long postId = persistPost(author, true);
        Cookie cookie = accessCookie(reader);

        // when / then
        mockMvc.perform(post("/api/posts/{id}/likes", postId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/posts/{id}/likes", postId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByMe").value(true));
        mockMvc.perform(post("/api/posts/{id}/likes", postId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));
        assertThat(postLikes.countForPost(postId)).isEqualTo(1);
        mockMvc.perform(get("/api/posts/{id}", postId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByMe").value(true));
        mockMvc.perform(delete("/api/posts/{id}/likes", postId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.likedByMe").value(false));
        assertThat(postLikes.countForPost(postId)).isZero();
    }

    @Test
    @DisplayName("로그인하지 않거나 온보딩 전이면 게시글 변경을 차단할 수 있다.")
    void rejectUnauthenticatedWrites() throws Exception {
        // given
        User pending = users.saveAndFlush(UserFixture.create(Role.PENDING));
        Cookie cookie = accessCookie(pending);

        // when / then
        mockMvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON)
                        .content(BlogPostFixture.REQUEST_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/posts/1").contentType(MediaType.APPLICATION_JSON)
                        .content(BlogPostFixture.REQUEST_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/posts/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/posts").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content(BlogPostFixture.REQUEST_JSON)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/posts/1").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content(BlogPostFixture.REQUEST_JSON)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/posts/1").cookie(cookie)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"title\":\" \",\"content\":\"본문\",\"category\":\"Java\",\"publicPost\":true}",
            "{\"title\":\"제목\",\"content\":\" \",\"category\":\"Java\",\"publicPost\":true}",
            "{\"title\":\"제목\",\"content\":\"본문\",\"category\":\"Java\"}",
            "{\"title\":\"제목\",\"content\":\"본문\",\"publicPost\":true}",
            "{\"title\":\"제목\",\"content\":\"본문\",\"category\":\" \",\"publicPost\":true}"
    })
    @DisplayName("필수 입력이 누락된 생성·수정 요청을 거절할 수 있다.")
    void rejectInvalidBody(String body) throws Exception {
        // given
        User author = users.saveAndFlush(UserFixture.create(Role.USER));
        Cookie cookie = accessCookie(author);
        Long id = persistPost(author, true);

        // when / then
        mockMvc.perform(post("/api/posts").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(put("/api/posts/{id}", id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(posts.findById(id).orElseThrow().getTitle()).isEqualTo("첫 기록");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[\" \" ]", "[null]", "[\"1234567890123456789012345678901\"]",
            "[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\",\"10\",\"11\"]"
    })
    @DisplayName("비어 있거나 길이·개수 제한을 넘는 태그를 거절할 수 있다.")
    void rejectInvalidTags(String tags) throws Exception {
        // given
        var author = users.saveAndFlush(UserFixture.create(Role.USER));
        String body = """
                {"title":"제목","content":"본문","category":"Java","tags":%s,"publicPost":true}
                """.formatted(tags);

        // when / then
        mockMvc.perform(post("/api/posts").cookie(accessCookie(author))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        assertThat(posts.count()).isZero();
    }

    @Test
    @DisplayName("공개 글과 내 글 목록을 카테고리와 태그로 검색할 수 있다.")
    void filterPostLists() throws Exception {
        // given
        var author = users.saveAndFlush(UserFixture.create(Role.USER));
        var cookie = accessCookie(author);
        mockMvc.perform(post("/api/posts").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(BlogPostFixture.REQUEST_JSON))
                .andExpect(status().isCreated());

        // when / then
        for (String path : new String[]{"/api/posts", "/api/posts/mine"}) {
            mockMvc.perform(get(path).cookie(cookie).param("category", "Java").param("tag", "SPRING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].category").value("Java"))
                    .andExpect(jsonPath("$.content[0].tags").value(containsInAnyOrder("java", "spring")));
            mockMvc.perform(get(path).cookie(cookie).param("tag", "vue"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }

    private Long persistPost(User author, boolean publicPost) {
        BlogPost post = BlogPostFixture.create(author, publicPost);
        categories.saveAndFlush(post.getCategory());
        return posts.saveAndFlush(post).getId();
    }

    private Cookie accessCookie(User user) {
        return new Cookie("access_token", tokens.generateTokenPair(
                user.getId(), user.getRole().name(), UUID.randomUUID()).accessToken());
    }
}
