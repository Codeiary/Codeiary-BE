package com.codeiary.domain.images.controller;

import com.codeiary.domain.images.dto.request.ImagePresignRequest;
import com.codeiary.domain.images.dto.response.ImageUploadResponse;
import com.codeiary.domain.images.service.ImageService;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.GlobalExceptionHandler;
import com.codeiary.global.exception.RestApiException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {
    @Mock private ImageService imageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(UserFixture.createWithId(), null, List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(new ImageController(imageService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 사용자에게 업로드 URL을 발급하고 캐시를 막을 수 있다.")
    void presign() throws Exception {
        // given
        var request = new ImagePresignRequest("image/png", 128L);
        given(imageService.presign(UserFixture.ID, request)).willReturn(new ImageUploadResponse(
                "https://test.s3.ap-northeast-2.amazonaws.com/images/1/test.png?signature=test",
                "https://img.example.com/images/1/test.png", Map.of("content-type", "image/png"),
                Instant.parse("2026-10-08T01:05:00Z")));

        // when & then
        mockMvc.perform(post("/api/images/presigned-url").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"contentLength\":128,\"userId\":999}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.uploadUrl").isString())
                .andExpect(jsonPath("$.imageUrl").value("https://img.example.com/images/1/test.png"))
                .andExpect(jsonPath("$.headers.content-type").value("image/png"))
                .andExpect(jsonPath("$.expiresAt").isString());
        then(imageService).should().presign(UserFixture.ID, request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{\"contentType\":\"image/png\",\"contentLength\":null}",
            "{\"contentType\":null,\"contentLength\":128}", "{\"contentType\":\"image/png\",\"contentLength\":0}",
            "{\"contentType\":\"image/png\",\"contentLength\":-1}",
            "{\"contentType\":\"\",\"contentLength\":128}"})
    @DisplayName("필수 항목이 없거나 파일 크기가 잘못되면 거절할 수 있다.")
    void rejectInvalidRequest(String body) throws Exception {
        // when & then
        mockMvc.perform(post("/api/images/presigned-url").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        then(imageService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("최대 파일 크기를 넘는 요청에 용량 초과 오류를 반환할 수 있다.")
    void rejectOversizedRequest() throws Exception {
        // given
        var request = new ImagePresignRequest("image/png", 10485761L);
        given(imageService.presign(UserFixture.ID, request))
                .willThrow(new RestApiException(CommonErrorCode.PAYLOAD_TOO_LARGE));

        // when & then
        mockMvc.perform(post("/api/images/presigned-url").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"contentLength\":10485761}"))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }
}
