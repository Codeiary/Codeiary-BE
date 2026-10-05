package com.codeiary.global.auth.controller;

import com.codeiary.global.auth.dto.response.TokenResponse;
import com.codeiary.global.auth.fixture.AuthRequestFixture;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.global.auth.repository.RefreshTokenRepository;
import com.codeiary.domain.users.repository.UserRepository;
import com.codeiary.global.auth.repository.TokenBlacklistRepository;
import com.codeiary.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthLogoutIntegrationTest extends IntegrationTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private TokenBlacklistRepository blacklist;
    @Autowired private PasswordEncoder passwords;

    @BeforeEach
    void createUser() {
        tokens.deleteAllInBatch();
        blacklist.deleteAllInBatch();
        users.deleteAllInBatch();
        users.saveAndFlush(UserFixture.create(passwords));
    }

    @Test
    @DisplayName("로그아웃한 토큰만 차단하고 다른 로그인을 유지할 수 있다.")
    void blockLoggedOutSession() throws Exception {
        // given
        var original = login();
        var otherLogin = login();
        var refreshed = refresh(original.refreshToken());
        mvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + original.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(original.user().id()))
                .andExpect(jsonPath("$.email").value(UserFixture.EMAIL))
                .andExpect(jsonPath("$.name").value(UserFixture.NAME))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshed.accessToken()))
                .andExpect(status().isOk());

        // when: a rotated token must still be able to log out its whole session.
        logout(original.refreshToken());
        logout(original.refreshToken());
        logout(refreshed.refreshToken());

        // then
        for (var accessToken : new String[]{original.accessToken(), refreshed.accessToken()}) {
            mvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(AuthRequestFixture.refresh(refreshed.refreshToken()))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        mvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherLogin.accessToken()))
                .andExpect(status().isOk());
        assertThat(refresh(otherLogin.refreshToken()).accessToken()).isNotBlank();
        assertThat(blacklist.count()).isEqualTo(1);
        assertThat(original.expiresIn()).isEqualTo(1800);
        assertThat(original.refreshExpiresIn()).isEqualTo(604800);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("삭제되거나 비활성인 계정의 토큰을 거절할 수 있다.")
    void rejectUnavailableUser(boolean disableUser) throws Exception {
        // given
        var session = login();
        var user = users.findById(session.user().id()).orElseThrow();
        if (disableUser) {
            user.disable();
            users.saveAndFlush(user);
        } else {
            users.deleteById(user.getId());
        }

        // when
        var result = mvc.perform(get("/api/admin/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private TokenResponse login() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(AuthRequestFixture.login())))
                .andExpect(status().isOk()).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
    }

    private TokenResponse refresh(String refreshToken) throws Exception {
        var result = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(AuthRequestFixture.refresh(refreshToken))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
    }

    private void logout(String refreshToken) throws Exception {
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(AuthRequestFixture.refresh(refreshToken))))
                .andExpect(status().isNoContent());
    }
}
