package com.codeiary.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeiary.domain.auth.dto.TokenPair;
import com.codeiary.domain.auth.provider.JwtTokenProvider;
import com.codeiary.domain.auth.service.TokenService;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
class SecurityIntegrationTest extends IntegrationTestSupport {

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository users;
  @Autowired private JwtTokenProvider tokens;
  @Autowired private TokenService tokenService;
  @Autowired private RedisTemplate<String, String> redis;
  @Autowired private EntityManager entityManager;

  @Test
  @DisplayName("인증 없이 헬스 체크를 조회할 수 있다.")
  void checkHealthWithoutAuthentication() throws Exception {
    mockMvc
        .perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }

  @Test
  @DisplayName("허용된 프론트엔드의 쿠키 인증 사전 요청을 처리할 수 있다.")
  void allowFrontendPreflight() throws Exception {
    mockMvc
        .perform(
            options("/users/me")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
  }

  @Test
  @DisplayName("인증이 없는 이미지 업로드를 차단할 수 있다.")
  void rejectAnonymousImageUpload() throws Exception {
    // when & then
    mockMvc
        .perform(
            post("/images/presigned-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/png\",\"contentLength\":128}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("공개 프로필과 인증이 필요한 사용자 기능을 구분할 수 있다.")
  void protectUserProfileRequests() throws Exception {
    // given
    String profile = "{\"nickname\":\"기록자\"}";

    // when & then
    mockMvc
        .perform(get("/users/nickname-availability").param("nickname", "기록자"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(multipart("/users/me/onboarding").param("nickname", "기록자"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(put("/users/me/profile").contentType(MediaType.APPLICATION_JSON).content(profile))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/users/{userId}", Long.MAX_VALUE))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND_USER"));
    mockMvc
        .perform(get("/users/by-nickname/{nickname}", "unregistered_987654"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND_USER"));
  }

  @Test
  @Transactional
  @DisplayName("온보딩을 완료하면 같은 토큰으로 사용자 권한을 적용할 수 있다.")
  void completePendingOnboarding() throws Exception {
    // given
    User user = users.saveAndFlush(UserFixture.create(Role.PENDING));
    Cookie accessCookie = accessCookie(user);

    // when & then
    mockMvc
        .perform(get("/users/me").cookie(accessCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("PENDING"));
    mockMvc
        .perform(get("/users/nickname-availability").param("nickname", "새기록자").cookie(accessCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true));
    mockMvc
        .perform(multipart("/users/me/onboarding").param("nickname", "").cookie(accessCookie))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/users/me").cookie(accessCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("PENDING"))
        .andExpect(jsonPath("$.onboardingCompleted").value(false));
    mockMvc
        .perform(multipart("/users/me/onboarding").param("nickname", "새기록자").cookie(accessCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.onboardingCompleted").value(true));

    entityManager.clear();
    assertThat(tokens.validateAccessToken(accessCookie.getValue()).get("role", String.class))
        .isEqualTo("PENDING");
    mockMvc
        .perform(
            put("/users/me/profile")
                .cookie(accessCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"기록자\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/users/me").cookie(accessCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("USER"));
  }

  @Test
  @Transactional
  @DisplayName("온보딩 전에는 보호된 변경 요청과 관리자 접근을 차단할 수 있다.")
  void rejectPendingProtectedRequests() throws Exception {
    // given
    User user = users.saveAndFlush(UserFixture.create(Role.PENDING));
    Cookie accessCookie = accessCookie(user);

    // when & then
    mockMvc
        .perform(
            put("/users/me/profile")
                .cookie(accessCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"우회차단\"}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/admin/me").cookie(accessCookie)).andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/images/presigned-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/gif\",\"contentLength\":128}")
                .cookie(accessCookie))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DENIED_ACCESS"));
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"USER", "ADMIN"})
  @Transactional
  @DisplayName("일반 사용자와 관리자의 기존 접근 권한을 유지할 수 있다.")
  void preserveMemberPermissions(Role role) throws Exception {
    // given
    User user = users.saveAndFlush(UserFixture.create(role));
    Cookie accessCookie = accessCookie(user);

    // when & then
    mockMvc
        .perform(
            put("/users/me/profile")
                .cookie(accessCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"기록자\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/admin/me").cookie(accessCookie))
        .andExpect(role == Role.ADMIN ? status().isOk() : status().isForbidden());
    mockMvc
        .perform(
            post("/images/presigned-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/gif\",\"contentLength\":128}")
                .cookie(accessCookie))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
  }

  @Test
  @Transactional
  @DisplayName("온보딩 전에도 토큰을 재발급하고 로그아웃할 수 있다.")
  void allowPendingTokenLifecycle() throws Exception {
    // given
    User user = users.saveAndFlush(UserFixture.create(Role.PENDING));
    TokenPair tokenPair = tokenService.login(user.getEmail());

    // when
    MvcResult mvcResult =
        mockMvc
            .perform(
                post("/auth/reissue").cookie(new Cookie("refresh_token", tokenPair.refreshToken())))
            .andExpect(status().isNoContent())
            .andReturn();
    Cookie accessCookie = mvcResult.getResponse().getCookie("access_token");
    Cookie refreshCookie = mvcResult.getResponse().getCookie("refresh_token");
    assertThat(accessCookie).isNotNull();
    assertThat(refreshCookie).isNotNull();

    // then
    mockMvc
        .perform(get("/users/me").cookie(accessCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("PENDING"));
    mockMvc
        .perform(post("/auth/logout").cookie(accessCookie, refreshCookie))
        .andExpect(status().isNoContent());
    mockMvc.perform(get("/users/me").cookie(accessCookie)).andExpect(status().isUnauthorized());
  }

  @Test
  @Transactional
  @DisplayName("교체된 Refresh Token을 다시 사용하면 새 Access Token도 차단할 수 있다.")
  void revokeSessionOnRefreshTokenReuse() throws Exception {
    User user = users.saveAndFlush(UserFixture.create(Role.USER));
    TokenPair tokenPair = tokenService.login(user.getEmail());

    MvcResult mvcResult =
        mockMvc
            .perform(
                post("/auth/reissue").cookie(new Cookie("refresh_token", tokenPair.refreshToken())))
            .andExpect(status().isNoContent())
            .andReturn();
    Cookie newAccess = mvcResult.getResponse().getCookie("access_token");
    assertThat(newAccess).isNotNull();

    mockMvc
        .perform(
            post("/auth/reissue").cookie(new Cookie("refresh_token", tokenPair.refreshToken())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    mockMvc.perform(get("/users/me").cookie(newAccess)).andExpect(status().isUnauthorized());
  }

  @Test
  @Transactional
  @DisplayName("리프레시 토큰 키가 사라져도 블랙리스트에 없는 Access Token은 사용할 수 있다.")
  void allowAccessTokenAfterRefreshKeyLoss() throws Exception {
    User user = users.saveAndFlush(UserFixture.create(Role.USER));
    TokenPair tokenPair = tokenService.login(user.getEmail());
    UUID loginId =
        UUID.fromString(
            tokens.validateAccessToken(tokenPair.accessToken()).get("sid", String.class));

    redis.delete("auth:refresh:{" + loginId + "}");

    mockMvc
        .perform(get("/users/me").cookie(new Cookie("access_token", tokenPair.accessToken())))
        .andExpect(status().isOk());
  }

  private Cookie accessCookie(User user) {
    return new Cookie("access_token", tokenService.login(user.getEmail()).accessToken());
  }
}
