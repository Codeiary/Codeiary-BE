package com.codeiary.domain.comment.controller;

import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.entity.Category;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.blog.repository.CategoryRepository;
import com.codeiary.domain.auth.provider.JwtTokenProvider;
import com.codeiary.domain.comment.repository.CommentRepository;
import com.codeiary.domain.comment.entity.CommentTargetType;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class CommentControllerTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private BlogPostRepository posts;
    @Autowired private CategoryRepository categories;
    @Autowired private CommentRepository comments;
    @Autowired private JwtTokenProvider tokens;

    @Test
    @DisplayName("댓글과 한 단계 답글을 작성·수정·삭제하고 조회할 수 있다.")
    void manageCommentThread() throws Exception {
        // given
        User author = users.saveAndFlush(UserFixture.create(Role.USER));
        author.updateProfile("기록자", "https://image.example.com/profile.png");
        Long postId = persistPost(author);
        Cookie cookie = accessCookie(author);

        // when
        String rootJson = mockMvc.perform(post("/api/posts/{postId}/comments", postId).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"댓글 😀\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/comments/")))
                .andExpect(jsonPath("$.author.nickname").value("기록자"))
                .andExpect(jsonPath("$.author.profileImageUrl").value("https://image.example.com/profile.png"))
                .andReturn().getResponse().getContentAsString();
        Long rootId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(rootJson).get("id").asLong();
        String replyJson = mockMvc.perform(post("/api/posts/{postId}/comments", postId).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"답글 🎉\",\"parentId\":" + rootId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(rootId))
                .andReturn().getResponse().getContentAsString();
        Long replyId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(replyJson).get("id").asLong();

        // then
        mockMvc.perform(get("/api/posts/{postId}/comments", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("댓글 😀"))
                .andExpect(jsonPath("$[1].content").value("답글 🎉"))
                .andExpect(jsonPath("$[1].parentId").value(rootId));
        mockMvc.perform(put("/api/posts/{postId}/comments/{commentId}", postId, rootId).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"수정한 댓글\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("수정한 댓글"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        mockMvc.perform(delete("/api/posts/{postId}/comments/{commentId}", postId, rootId).cookie(cookie))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/posts/{postId}/comments", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deleted").value(true))
                .andExpect(jsonPath("$[0].author").doesNotExist())
                .andExpect(jsonPath("$[0].content").value(""))
                .andExpect(jsonPath("$[1].id").value(replyId));
        assertThat(comments.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("로그인하지 않은 사용자와 다른 사용자의 댓글 변경을 차단할 수 있다.")
    void rejectUnauthorizedCommentChanges() throws Exception {
        // given
        User author = users.saveAndFlush(UserFixture.create(Role.USER));
        User other = users.saveAndFlush(UserFixture.create("other@example.com"));
        Long postId = persistPost(author);
        Cookie authorCookie = accessCookie(author);
        Cookie otherCookie = accessCookie(other);

        // when / then
        mockMvc.perform(post("/api/posts/{postId}/comments", postId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"익명 댓글\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/posts/{postId}/comments", postId).cookie(authorCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"작성자 댓글\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.id").value(author.getId()));
        Long commentId = comments.findAllByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
                CommentTargetType.BLOG_POST, postId).getFirst().getId();
        mockMvc.perform(put("/api/posts/{postId}/comments/{commentId}", postId, commentId).cookie(authorCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"수정\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/posts/{postId}/comments/{commentId}", postId, commentId).cookie(otherCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"가로채기\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/posts/{postId}/comments/{commentId}", postId, commentId).cookie(otherCookie))
                .andExpect(status().isForbidden());
        assertThat(comments.findById(commentId).orElseThrow().getContent()).isEqualTo("수정");
    }

    @Test
    @DisplayName("빈 댓글과 2,000자를 넘는 댓글을 거절할 수 있다.")
    void rejectInvalidComment() throws Exception {
        // given
        User author = users.saveAndFlush(UserFixture.create(Role.USER));
        Long postId = persistPost(author);
        Cookie cookie = accessCookie(author);

        // when / then
        for (String content : new String[]{" ", "가".repeat(2001)}) {
            mockMvc.perform(post("/api/posts/{postId}/comments", postId).cookie(cookie)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                                    java.util.Map.of("content", content))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(comments.count()).isZero();
    }

    private Long persistPost(User author) {
        Category category = categories.saveAndFlush(Category.create(author, "개발"));
        return posts.saveAndFlush(BlogPost.create(author, "글", "본문", category, null, true)).getId();
    }

    private Cookie accessCookie(User user) {
        return new Cookie("access_token", tokens.generateTokenPair(
                user.getId(), user.getRole().name(), UUID.randomUUID()).accessToken());
    }
}
