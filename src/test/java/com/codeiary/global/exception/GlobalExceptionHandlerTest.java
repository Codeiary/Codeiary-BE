package com.codeiary.global.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("비즈니스 예외를 응답할 수 있다.")
    void handleBusinessException() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("서버 오류의 내부 정보 노출을 차단할 수 있다.")
    void hideInternalDetails() throws Exception {
        mockMvc.perform(get("/test/server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("DB 오류의 SQL 정보 노출을 차단할 수 있다.")
    void hideSqlDetails() throws Exception {
        mockMvc.perform(get("/test/database-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(DuplicateKeyException.class))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("요청 본문 검증 오류를 응답할 수 있다.")
    void validateRequestBody() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("이름은 필수입니다."));
    }

    @Test
    @DisplayName("잘못된 JSON에 400을 응답할 수 있다.")
    void rejectMalformedJson() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("쿼리 파라미터의 타입 오류를 응답할 수 있다.")
    void handleQueryTypeMismatch() throws Exception {
        mockMvc.perform(get("/test/parameter").param("count", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.message").value("count: 요청 값의 타입이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("누락된 쿼리 파라미터를 응답할 수 있다.")
    void handleMissingParameter() throws Exception {
        mockMvc.perform(get("/test/parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PARAMETER"))
                .andExpect(jsonPath("$.message").value("count: 필수 요청 파라미터가 누락되었습니다."));
    }

    @Test
    @DisplayName("405 응답에 허용 메서드를 포함할 수 있다.")
    void handleUnsupportedMethod() throws Exception {
        mockMvc.perform(post("/test/parameter"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("415 응답에 지원 형식을 포함할 수 있다.")
    void handleUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string(HttpHeaders.ACCEPT, containsString("application/json")))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("없는 리소스에 404를 응답할 수 있다.")
    void handleMissingResource() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("메서드 파라미터 검증 오류를 응답할 수 있다.")
    void validateMethodParameter() throws Exception {
        mockMvc.perform(get("/test/method-validation").param("count", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("count는 1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("반환값 검증 오류의 노출을 차단할 수 있다.")
    void validateReturnValue() throws Exception {
        mockMvc.perform(get("/test/return-validation"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    public record InputRequest(@NotBlank(message = "이름은 필수입니다.") String name) {
    }

    @TestComponent
    @RestController
    @RequestMapping("/test")
    public static class TestController {

        @GetMapping("/business")
        public void business() {
            throw new RestApiException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }

        @GetMapping("/server-error")
        public void serverError() {
            throw new IllegalStateException("internal-secret");
        }

        @GetMapping("/database-error")
        public void databaseError() {
            throw new DuplicateKeyException("INSERT INTO users: private database detail");
        }

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        public InputRequest body(@Valid @RequestBody InputRequest request) {
            return request;
        }

        @GetMapping("/parameter")
        public int parameter(@RequestParam int count) {
            return count;
        }

        @GetMapping("/method-validation")
        public int methodValidation(
                @RequestParam @Min(value = 1, message = "count는 1 이상이어야 합니다.") int count) {
            return count;
        }

        @NotNull(message = "internal validation detail")
        @GetMapping("/return-validation")
        public String returnValidation() {
            return null;
        }
    }
}
