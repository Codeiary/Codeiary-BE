package com.codeiary.domain.user.controller;

import com.codeiary.domain.user.dto.response.PublicUserProfileResponse;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.service.UserService;
import com.codeiary.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicUserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicUserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("공개 프로필에서 로그인 정보를 제외할 수 있다.")
    void getPublicProfile() throws Exception {
        // given
        PublicUserProfileResponse profile = new PublicUserProfileResponse(
                UserFixture.ID, "기록자", null, "https://github.com/writer", "contact@example.com");
        given(userService.getPublicProfile(UserFixture.ID)).willReturn(profile);

        // when
        MvcResult result = mockMvc.perform(get("/api/users/{userId}", UserFixture.ID)).andReturn();

        // then
        status().isOk().match(result);
        jsonPath("$.nickname").value("기록자").match(result);
        jsonPath("$.githubUrl").value("https://github.com/writer").match(result);
        jsonPath("$.contactEmail").value("contact@example.com").match(result);
        jsonPath("$.email").doesNotExist().match(result);
        jsonPath("$.name").doesNotExist().match(result);
        jsonPath("$.role").doesNotExist().match(result);
        then(userService).should().getPublicProfile(UserFixture.ID);
    }

    @Test
    @DisplayName("닉네임으로 공개 프로필을 조회할 수 있다.")
    void getPublicProfileByNickname() throws Exception {
        // given
        PublicUserProfileResponse profile = new PublicUserProfileResponse(UserFixture.ID, "기록자", null, null, null);
        given(userService.getPublicProfileByNickname("기록자")).willReturn(profile);

        // when
        MvcResult result = mockMvc.perform(get("/api/users/by-nickname/{nickname}", "기록자")).andReturn();

        // then
        status().isOk().match(result);
        jsonPath("$.id").value(UserFixture.ID).match(result);
        jsonPath("$.nickname").value("기록자").match(result);
        then(userService).should().getPublicProfileByNickname("기록자");
    }
}
