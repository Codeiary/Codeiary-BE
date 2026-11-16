package com.codeiary.global.security.exception;

import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.RestApiException;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityErrorHandlerTest {

    @DisplayName("서버 오류의 내부 원인 노출을 차단할 수 있다.")
    @Test
    void serverErrorDoesNotExposeCause() throws IOException {
        var handler = new SecurityErrorHandler(new ObjectMapper());
        var response = new MockHttpServletResponse();
        var exception = new RestApiException(CommonErrorCode.INTERNAL_SERVER_ERROR,
                new IllegalStateException("Redis blacklist lookup returned null"));

        handler.handleServerError(response, exception);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString())
                .contains("INTERNAL_SERVER_ERROR")
                .doesNotContain("Redis blacklist");
    }
}
