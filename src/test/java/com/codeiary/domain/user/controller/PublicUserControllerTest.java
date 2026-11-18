package com.codeiary.domain.user.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeiary.domain.user.dto.response.PublicUserProfileResponse;
import com.codeiary.domain.user.dto.response.UserNeighborhoodPageResponse;
import com.codeiary.domain.user.dto.response.UserNeighborhoodResponse;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.service.UserService;
import com.codeiary.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PublicUserControllerTest {

  @Mock private UserService userService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new PublicUserController(userService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("공개 프로필에서 로그인 정보를 제외할 수 있다.")
  void getPublicProfile() throws Exception {
    // given
    PublicUserProfileResponse publicUserProfileResponse =
        new PublicUserProfileResponse(
            UserFixture.ID, "기록자", null, "https://github.com/writer", "contact@example.com");
    given(userService.getPublicProfile(UserFixture.ID)).willReturn(publicUserProfileResponse);

    // when
    MvcResult mvcResult = mockMvc.perform(get("/users/{userId}", UserFixture.ID)).andReturn();

    // then
    status().isOk().match(mvcResult);
    jsonPath("$.nickname").value("기록자").match(mvcResult);
    jsonPath("$.githubUrl").value("https://github.com/writer").match(mvcResult);
    jsonPath("$.contactEmail").value("contact@example.com").match(mvcResult);
    jsonPath("$.email").doesNotExist().match(mvcResult);
    jsonPath("$.name").doesNotExist().match(mvcResult);
    jsonPath("$.role").doesNotExist().match(mvcResult);
    then(userService).should().getPublicProfile(UserFixture.ID);
  }

  @Test
  @DisplayName("닉네임으로 공개 프로필을 조회할 수 있다.")
  void getPublicProfileByNickname() throws Exception {
    // given
    PublicUserProfileResponse publicUserProfileResponse =
        new PublicUserProfileResponse(UserFixture.ID, "기록자", null, null, null);
    given(userService.getPublicProfileByNickname("기록자")).willReturn(publicUserProfileResponse);

    // when
    MvcResult mvcResult = mockMvc.perform(get("/users/by-nickname/{nickname}", "기록자")).andReturn();

    // then
    status().isOk().match(mvcResult);
    jsonPath("$.id").value(UserFixture.ID).match(mvcResult);
    jsonPath("$.nickname").value("기록자").match(mvcResult);
    then(userService).should().getPublicProfileByNickname("기록자");
  }

  @Test
  @DisplayName("이웃 사용자를 조회할 수 있다.")
  void getNeighborhood() throws Exception {
    // given
    var residents =
        List.of(
            new UserNeighborhoodResponse(
                UserFixture.ID, "기록자", null, "https://github.com/writer", null, 3));
    given(userService.getNeighborhood(0, 10))
        .willReturn(new UserNeighborhoodPageResponse(residents, 0, 10, 1, 1, true, true));

    // when
    MvcResult mvcResult = mockMvc.perform(get("/users/neighborhood")).andReturn();

    // then
    status().isOk().match(mvcResult);
    jsonPath("$.content[0].nickname").value("기록자").match(mvcResult);
    jsonPath("$.content[0].postCount").value(3).match(mvcResult);
    jsonPath("$.totalElements").value(1).match(mvcResult);
    then(userService).should().getNeighborhood(0, 10);
  }
}
