package com.codeiary.domain.user.controller;

import com.codeiary.domain.user.dto.request.UpdateProfileRequest;
import com.codeiary.domain.user.dto.response.NicknameAvailabilityResponse;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.service.UserService;
import com.codeiary.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(UserFixture.createWithId(), null, List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("내 계정을 기준으로 닉네임 중복을 확인할 수 있다.")
    void checkNickname() throws Exception {
        // given
        given(userService.checkNickname(UserFixture.ID, "기록자"))
                .willReturn(new NicknameAvailabilityResponse(true));

        // when
        MvcResult result = mockMvc.perform(get("/api/users/nickname-availability")
                .param("nickname", "기록자")).andReturn();

        // then
        status().isOk().match(result);
        jsonPath("$.available").value(true).match(result);
        then(userService).should().checkNickname(UserFixture.ID, "기록자");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "x", "두 단어"})
    @DisplayName("비어 있거나 잘못된 닉네임을 거절할 수 있다.")
    void rejectInvalidOnboarding(String nickname) throws Exception {
        // when
        MvcResult result = mockMvc.perform(multipart("/api/users/me/onboarding")
                .param("nickname", nickname)).andReturn();

        // then
        status().isBadRequest().match(result);
        then(userService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("내 프로필과 공개 연락처를 수정할 수 있다.")
    void updateProfile() throws Exception {
        // given
        UpdateProfileRequest request = new UpdateProfileRequest(
                "기록자", null, "https://github.com/writer", "contact@example.com");
        given(userService.updateProfile(UserFixture.ID, request)).willReturn(UserFixture.response());

        // when
        MvcResult result = mockMvc.perform(put("/api/users/me/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname":"기록자","githubUrl":"https://github.com/writer","contactEmail":"contact@example.com"}
                        """)).andReturn();

        // then
        status().isOk().match(result);
        then(userService).should().updateProfile(UserFixture.ID, request);
    }

    @Test
    @DisplayName("GitHub 프로필이 아닌 주소를 거절할 수 있다.")
    void rejectInvalidGithubUrl() throws Exception {
        // when
        MvcResult result = mockMvc.perform(put("/api/users/me/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname":"기록자","githubUrl":"https://example.com/writer"}
                        """)).andReturn();

        // then
        status().isBadRequest().match(result);
        then(userService).shouldHaveNoInteractions();
    }

}
