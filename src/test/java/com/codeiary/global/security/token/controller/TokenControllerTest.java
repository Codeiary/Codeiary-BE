package com.codeiary.global.security.token.controller;

import com.codeiary.global.exception.GlobalExceptionHandler;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.token.cookie.TokenProperties;
import com.codeiary.global.security.token.dto.TokenPair;
import com.codeiary.global.security.token.exception.TokenErrorCode;
import com.codeiary.global.security.token.service.TokenService;
import com.codeiary.global.security.token.service.TokenSessionService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TokenControllerTest {

    @Mock
    private TokenSessionService sessions;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TokenProperties properties = new TokenProperties(
                "access_token", "refresh_token", "codeiary.com", "/",
                true, true, "Strict", Duration.ofMinutes(30), Duration.ofDays(7));
        TokenService cookies = new TokenService(properties);
        mockMvc = MockMvcBuilders.standaloneSetup(new TokenController(sessions, cookies))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/reissue", "/refresh"})
    @DisplayName("Refresh Token 쿠키로 토큰을 재발급할 수 있다.")
    void reissueTokens(String path) throws Exception {
        // given
        Instant now = Instant.now();
        TokenPair pair = new TokenPair("new-access", "new-refresh",
                now.plus(Duration.ofMinutes(30)), now.plus(Duration.ofDays(7)));
        given(sessions.reissue("old-refresh")).willReturn(pair);

        // when
        MvcResult result = mockMvc.perform(post("/api/auth" + path)
                        .cookie(new Cookie("refresh_token", "old-refresh")))
                .andReturn();

        // then
        status().isNoContent().match(result);
        content().string("").match(result);
        header().string(HttpHeaders.CACHE_CONTROL, "no-store").match(result);
        header().doesNotExist(HttpHeaders.CONTENT_TYPE).match(result);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .hasSize(2)
                .anySatisfy(cookie -> assertThat(cookie).startsWith("access_token=new-access;"))
                .anySatisfy(cookie -> assertThat(cookie).startsWith("refresh_token=new-refresh;"))
                .allSatisfy(cookie -> assertThat(cookie)
                        .contains("Path=/", "Domain=codeiary.com", "Secure", "HttpOnly", "SameSite=Strict")
                        .containsPattern("Max-Age=[1-9][0-9]*"));
        then(sessions).should().reissue("old-refresh");
    }

    @Test
    @DisplayName("만료된 토큰의 재발급을 거절하고 쿠키를 삭제할 수 있다.")
    void rejectExpiredToken() throws Exception {
        // given
        given(sessions.reissue("expired-refresh"))
                .willThrow(new RestApiException(TokenErrorCode.TOKEN_EXPIRED));

        // when
        MvcResult result = mockMvc.perform(post("/api/auth/reissue")
                        .cookie(new Cookie("refresh_token", "expired-refresh")))
                .andReturn();

        // then
        status().isUnauthorized().match(result);
        jsonPath("$.code").value("TOKEN_EXPIRED").match(result);
        header().string(HttpHeaders.CACHE_CONTROL, "no-store").match(result);
        assertExpiredCookies(result.getResponse());
        then(sessions).should().reissue("expired-refresh");
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 없는 재발급 요청을 거절할 수 있다.")
    void rejectMissingCookie() throws Exception {
        // given
        given(sessions.reissue(null))
                .willThrow(new RestApiException(TokenErrorCode.TOKEN_MISSING));

        // when
        MvcResult result = mockMvc.perform(post("/api/auth/reissue")).andReturn();

        // then
        status().isUnauthorized().match(result);
        jsonPath("$.code").value("TOKEN_MISSING").match(result);
        assertExpiredCookies(result.getResponse());
        then(sessions).should().reissue(null);
    }

    @Test
    @DisplayName("인증 쿠키로 로그아웃하고 쿠키를 삭제할 수 있다.")
    void logout() throws Exception {
        // given
        Cookie accessCookie = new Cookie("access_token", "current-access");
        Cookie refreshCookie = new Cookie("refresh_token", "current-refresh");

        // when
        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .cookie(accessCookie, refreshCookie))
                .andReturn();

        // then
        status().isNoContent().match(result);
        content().string("").match(result);
        header().string(HttpHeaders.CACHE_CONTROL, "no-store").match(result);
        assertExpiredCookies(result.getResponse());
        then(sessions).should().logout("current-refresh", "current-access");
    }

    private void assertExpiredCookies(MockHttpServletResponse response) {
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .hasSize(2)
                .anySatisfy(cookie -> assertThat(cookie).startsWith("access_token=;"))
                .anySatisfy(cookie -> assertThat(cookie).startsWith("refresh_token=;"))
                .allSatisfy(cookie -> assertThat(cookie).contains(
                        "Max-Age=0", "Path=/", "Domain=codeiary.com", "Secure", "HttpOnly", "SameSite=Strict"));
    }
}
