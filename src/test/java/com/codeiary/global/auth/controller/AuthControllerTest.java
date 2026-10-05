package com.codeiary.global.auth.controller;

import com.codeiary.global.auth.dto.response.TokenResponse;
import com.codeiary.global.auth.exception.AuthErrorCode;
import com.codeiary.global.auth.fixture.AuthRequestFixture;
import com.codeiary.global.auth.fixture.AuthResponseFixture;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.support.ControllerTestSupport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends ControllerTestSupport {

    private final TokenResponse tokens = AuthResponseFixture.tokens();

    @Test
    @DisplayName("로그인하고 사용자와 토큰을 응답할 수 있다.")
    void login() throws Exception {
        // given
        var request = AuthRequestFixture.login();
        given(authService.login(request)).willReturn(tokens);

        // when
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.accessToken").value(tokens.accessToken()))
                .andExpect(jsonPath("$.refreshToken").value(tokens.refreshToken()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.refreshExpiresIn").value(604800))
                .andExpect(jsonPath("$.user.email").value(UserFixture.EMAIL))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
        then(authService).should().login(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ab123!!!", "Codeiary123456789!@#", "A!b@123#", "abcd1!!!", "ABCD1!!!"})
    @DisplayName("규칙에 맞는 비밀번호로 로그인할 수 있다.")
    void loginWithValidPassword(String password) throws Exception {
        // given
        var request = AuthRequestFixture.login(UserFixture.EMAIL, password);
        given(authService.login(request)).willReturn(tokens);

        // when
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isOk());
        then(authService).should().login(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ab12!!!", "Codeiary1234567890!@#", "12345!@#", "Abcde!@#", "Code123!@",
            "Code1234", "Ab123가나다", "Ab123   "})
    @DisplayName("규칙에 맞지 않는 비밀번호를 거절할 수 있다.")
    void rejectInvalidPassword(String password) throws Exception {
        // given
        var request = AuthRequestFixture.login(UserFixture.EMAIL, password);

        // when
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        then(authService).shouldHaveNoInteractions();
        then(jwtTokens).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin@example.com", "admin@EXAMPLE.COM"})
    @DisplayName("대문자 이메일을 거절할 수 있다.")
    void rejectUppercaseEmail(String email) throws Exception {
        // given
        var request = AuthRequestFixture.login(email, UserFixture.PASSWORD);

        // when
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("이메일은 소문자로 입력해 주세요."));
        then(authService).shouldHaveNoInteractions();
        then(jwtTokens).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("로그인 입력을 검증할 수 있다.")
    void validateLogin() throws Exception {
        // given
        var requests = List.of(AuthRequestFixture.login("not-an-email", UserFixture.PASSWORD),
                AuthRequestFixture.login(UserFixture.EMAIL, ""),
                AuthRequestFixture.login(UserFixture.EMAIL, null));

        // when
        List<ResultActions> results = new ArrayList<>();
        for (var request : requests) {
            results.add(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request))));
        }

        // then
        for (var result : results) {
            result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }
        then(authService).shouldHaveNoInteractions();
        then(jwtTokens).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("로그인 실패에 401을 반환할 수 있다.")
    void rejectInvalidCredentials() throws Exception {
        // given
        var request = AuthRequestFixture.login(UserFixture.EMAIL, UserFixture.WRONG_PASSWORD);
        given(authService.login(request)).willThrow(new RestApiException(AuthErrorCode.INVALID_CREDENTIALS));

        // when
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.INVALID_CREDENTIALS.getMessage()))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    @DisplayName("토큰을 갱신할 수 있다.")
    void refreshTokens() throws Exception {
        // given
        var request = AuthRequestFixture.refresh();
        given(authService.refresh(request.refreshToken())).willReturn(tokens);

        // when
        var result = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.accessToken").value(tokens.accessToken()))
                .andExpect(jsonPath("$.refreshToken").value(tokens.refreshToken()));
        then(authService).should().refresh(request.refreshToken());
    }

    @Test
    @DisplayName("갱신 실패에 401을 반환할 수 있다.")
    void rejectInvalidRefreshToken() throws Exception {
        // given
        var request = AuthRequestFixture.refresh();
        given(authService.refresh(request.refreshToken())).willThrow(new RestApiException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        // when
        var result = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-refresh-token", "header.payload.signature"})
    @DisplayName("갱신 토큰 형식을 검증할 수 있다.")
    void validateRefreshToken(String token) throws Exception {
        // given
        var request = AuthRequestFixture.refresh(token);
        var paths = List.of("/api/auth/refresh", "/api/auth/logout");

        // when
        List<ResultActions> results = new ArrayList<>();
        for (var path : paths) {
            results.add(mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request))));
        }

        // then
        for (var result : results) {
            result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }
        then(authService).shouldHaveNoInteractions();
        then(jwtTokens).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("로그아웃에 204를 반환할 수 있다.")
    void logout() throws Exception {
        // given
        var request = AuthRequestFixture.refresh();

        // when
        var result = mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
        then(authService).should().logout(request.refreshToken());
    }
}
