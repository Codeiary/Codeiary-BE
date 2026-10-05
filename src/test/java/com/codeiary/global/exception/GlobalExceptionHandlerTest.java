package com.codeiary.global.exception;

import com.codeiary.support.IntegrationTestSupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("비즈니스 예외의 상태 코드와 메시지를 응답할 수 있다.")
    void businessExceptionReturnsItsStatusCodeAndMessage() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("예상하지 못한 예외를 내부 정보 없이 서버 오류로 응답할 수 있다.")
    void unexpectedExceptionDoesNotExposeInternalDetails() throws Exception {
        mockMvc.perform(get("/test/server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("실제 DB 중복 키 오류를 SQL 정보 없이 서버 오류로 응답할 수 있다.")
    void databaseExceptionDoesNotExposeSqlDetails() throws Exception {
        mockMvc.perform(get("/test/database-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(DuplicateKeyException.class))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("요청 본문의 검증 실패 메시지를 응답할 수 있다.")
    void invalidRequestBodyReturnsValidationMessage() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("이름은 필수입니다."));
    }

    @Test
    @DisplayName("잘못된 JSON 요청에 400 오류를 응답할 수 있다.")
    void malformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("필수 요청 본문이 누락되면 400 오류를 응답할 수 있다.")
    void missingRequestBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("쿼리 파라미터의 타입 오류와 파라미터 이름을 응답할 수 있다.")
    void invalidQueryParameterTypeIdentifiesTheParameter() throws Exception {
        mockMvc.perform(get("/test/parameter").param("count", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.message").value("count: 요청 값의 타입이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("경로 변수의 타입 오류와 변수 이름을 응답할 수 있다.")
    void invalidPathVariableTypeIdentifiesTheVariable() throws Exception {
        mockMvc.perform(get("/test/items/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.message").value("id: 요청 값의 타입이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("누락된 필수 쿼리 파라미터 이름을 응답할 수 있다.")
    void missingQueryParameterIdentifiesTheParameter() throws Exception {
        mockMvc.perform(get("/test/parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PARAMETER"))
                .andExpect(jsonPath("$.message").value("count: 필수 요청 파라미터가 누락되었습니다."));
    }

    @Test
    @DisplayName("누락된 필수 요청 헤더 이름을 응답할 수 있다.")
    void missingHeaderIdentifiesTheHeader() throws Exception {
        mockMvc.perform(get("/test/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_HEADER"))
                .andExpect(jsonPath("$.message").value("X-Request-Id: 필수 요청 헤더가 누락되었습니다."));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드에 허용된 메서드와 405 오류를 응답할 수 있다.")
    void unsupportedMethodPreservesTheAllowHeader() throws Exception {
        mockMvc.perform(post("/test/parameter"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type에 지원하는 형식과 415 오류를 응답할 수 있다.")
    void unsupportedContentTypePreservesSupportedMediaTypes() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string(HttpHeaders.ACCEPT, containsString("application/json")))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("지원하지 않는 응답 형식에 JSON 형식의 406 오류를 응답할 수 있다.")
    void unsupportedResponseTypeStillReturnsAnErrorBody() throws Exception {
        mockMvc.perform(get("/test/json").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }

    @Test
    @DisplayName("존재하지 않는 리소스에 공통 형식의 404 오류를 응답할 수 있다.")
    void missingResourceUsesTheSameErrorFormat() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("잘못된 경로 변수 매핑을 서버 오류로 응답할 수 있다.")
    void incorrectPathVariableMappingIsAServerError() throws Exception {
        mockMvc.perform(get("/test/missing-path"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("메서드 파라미터의 검증 실패 메시지를 응답할 수 있다.")
    void invalidMethodParameterReturnsValidationMessage() throws Exception {
        mockMvc.perform(get("/test/method-validation").param("count", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("count는 1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("반환값 검증 실패를 내부 정보 없이 서버 오류로 응답할 수 있다.")
    void invalidReturnValueIsAServerErrorWithoutValidationDetails() throws Exception {
        mockMvc.perform(get("/test/return-validation"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("유효한 요청에 정상 응답을 반환할 수 있다.")
    void validRequestIsNotChangedByTheExceptionHandler() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Codeiary\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Codeiary"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    public record InputRequest(@NotBlank(message = "이름은 필수입니다.") String name) {
    }

    @TestComponent
    @RestController
    @RequestMapping("/test")
    public static class TestController {

        private final JdbcTemplate jdbcTemplate;

        public TestController(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @GetMapping("/business")
        public void business() {
            throw new RestApiException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }

        @GetMapping("/server-error")
        public void serverError() {
            throw new IllegalStateException("internal-secret");
        }

        @GetMapping("/database-error")
        @Transactional
        public void databaseError() {
            jdbcTemplate.execute("CREATE TEMP TABLE exception_handler_test (id INTEGER PRIMARY KEY)");
            jdbcTemplate.update("INSERT INTO exception_handler_test (id) VALUES (1)");
            jdbcTemplate.update("INSERT INTO exception_handler_test (id) VALUES (1)");
        }

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        public InputRequest body(@Valid @RequestBody InputRequest request) {
            return request;
        }

        @GetMapping("/parameter")
        public int parameter(@RequestParam("count") int count) {
            return count;
        }

        @GetMapping("/items/{id}")
        public long item(@PathVariable("id") long id) {
            return id;
        }

        @GetMapping("/header")
        public String header(@RequestHeader("X-Request-Id") String requestId) {
            return requestId;
        }

        @GetMapping(value = "/json", produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, String> json() {
            return Map.of("message", "ok");
        }

        @GetMapping("/missing-path")
        public long missingPath(@PathVariable("id") long id) {
            return id;
        }

        @GetMapping("/method-validation")
        public int methodValidation(
                @RequestParam("count") @Min(value = 1, message = "count는 1 이상이어야 합니다.") int count) {
            return count;
        }

        @NotNull(message = "internal validation detail")
        @GetMapping("/return-validation")
        public String returnValidation() {
            return null;
        }
    }
}
