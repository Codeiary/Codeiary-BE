package com.codeiary.domain.users.controller;

import com.codeiary.domain.users.entity.User;
import com.codeiary.domain.users.entity.enums.Role;
import com.codeiary.domain.users.service.AdminService;
import com.codeiary.global.auth.fixture.AuthResponseFixture;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.global.auth.exception.SecurityErrorCode;
import com.codeiary.global.auth.fixture.JwtFixture;
import com.codeiary.support.ControllerTestSupport;
import io.jsonwebtoken.JwtException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
class AdminControllerTest extends ControllerTestSupport {

    @MockitoBean private AdminService adminService;

    private final User user = UserFixture.createWithId();

    @Test
    @DisplayName("관리자 정보를 조회할 수 있다.")
    void getCurrentAdmin() throws Exception {
        // given
        String token = AuthResponseFixture.ACCESS_TOKEN;
        given(jwtTokens.validateAccessToken(token)).willReturn(JwtFixture.claims("ADMIN"));
        given(authService.loadActiveUser(user.getId())).willReturn(user);
        given(adminService.getProfile(user)).willReturn(UserFixture.response());

        // when
        var result = mvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(UserFixture.ID))
                .andExpect(jsonPath("$.email").value(UserFixture.EMAIL))
                .andExpect(jsonPath("$.name").value(UserFixture.NAME))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertThat(result.andReturn().getRequest().getSession(false)).isNull();
        then(authService).should().loadActiveUser(user.getId());
        then(authService).shouldHaveNoMoreInteractions();
        then(adminService).should().getProfile(user);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ADMIN"})
    @DisplayName("사용자의 관리자 조회를 차단할 수 있다.")
    void rejectAdminLookupByUser(String tokenRole) throws Exception {
        // given
        String token = AuthResponseFixture.ACCESS_TOKEN;
        given(jwtTokens.validateAccessToken(token)).willReturn(JwtFixture.claims(tokenRole));
        given(authService.loadActiveUser(user.getId())).willReturn(UserFixture.createWithId(Role.USER));

        // when
        var result = mvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        then(adminService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인증 없는 관리자 조회를 차단할 수 있다.")
    void rejectAnonymousRequest() throws Exception {
        // given
        var request = get("/api/admin/me");

        // when
        var result = mvc.perform(request);

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        then(authService).shouldHaveNoInteractions();
        then(jwtTokens).shouldHaveNoInteractions();
        then(adminService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("유효하지 않은 JWT를 거절할 수 있다.")
    void rejectInvalidJwt() throws Exception {
        // given
        String token = "invalid-token";
        given(jwtTokens.validateAccessToken(token)).willThrow(new JwtException("signature validation failed"));

        // when
        var result = mvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(SecurityErrorCode.UNAUTHORIZED.getMessage()));
        then(authService).shouldHaveNoInteractions();
        then(adminService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인증 실패에 401을 응답할 수 있다.")
    void rejectUnavailableUser() throws Exception {
        // given
        String token = AuthResponseFixture.ACCESS_TOKEN;
        given(jwtTokens.validateAccessToken(token)).willReturn(JwtFixture.claims("ADMIN"));
        given(authService.loadActiveUser(user.getId())).willThrow(new BadCredentialsException("Unavailable user"));

        // when
        var result = mvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        then(adminService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-id", "9999999999999999999"})
    @DisplayName("잘못된 사용자 식별자로 인증을 차단할 수 있다.")
    void rejectInvalidSubject(String subject) throws Exception {
        // given
        String token = AuthResponseFixture.ACCESS_TOKEN;
        given(jwtTokens.validateAccessToken(token)).willReturn(JwtFixture.claims(subject, "ADMIN"));

        // when
        var result = mvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        // then
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        then(authService).shouldHaveNoInteractions();
        then(adminService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("잘못된 토큰 전달을 거절할 수 있다.")
    void rejectInvalidAuthorization() throws Exception {
        // given
        var requests = List.of(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, "Bearer"),
                get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, "Bearer "),
                get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, "Basic " + AuthResponseFixture.ACCESS_TOKEN),
                get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, "Bearer token", "Bearer token"),
                get("/api/admin/me").param("access_token", AuthResponseFixture.ACCESS_TOKEN));

        // when
        List<ResultActions> results = new ArrayList<>();
        for (var request : requests) {
            results.add(mvc.perform(request));
        }

        // then
        for (var result : results) {
            result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        then(authService).shouldHaveNoInteractions();
        then(jwtTokens).shouldHaveNoInteractions();
        then(adminService).shouldHaveNoInteractions();
    }

}
