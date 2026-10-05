package com.codeiary.global.config;

import com.codeiary.support.IntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(OpenApiIntegrationTest.TestController.class)
class OpenApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("인증 없이 OpenAPI 문서와 Swagger 설정을 조회할 수 있다.")
    void documentationAndSwaggerConfigurationAreAccessible() throws Exception {
        mockMvc.perform(get("/api/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Codeiary API"));
        mockMvc.perform(get("/api/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/api/v3/api-docs"));
    }

    @Test
    @DisplayName("API 프록시 경로에서 Swagger 화면과 리소스를 로드할 수 있다.")
    void swaggerUiAndAssetsUseTheApiPrefix() throws Exception {
        mockMvc.perform(get("/api/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/swagger-ui/index.html"));
        mockMvc.perform(get("/api/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("swagger-ui")));
        mockMvc.perform(get("/api/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v3/api-docs/swagger-config")));
        mockMvc.perform(get("/api/swagger-ui/swagger-ui.css"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증과 CSRF 토큰 없이 일반 API에 조회와 변경 요청을 할 수 있다.")
    void allEndpointsAllowAnonymousRequestsWithoutCsrf() throws Exception {
        mockMvc.perform(get("/api/test/security"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
        mockMvc.perform(post("/api/test/security"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @TestComponent
    @RestController
    @RequestMapping("/api/test/security")
    public static class TestController {

        @GetMapping
        public Map<String, String> get() {
            return Map.of("status", "ok");
        }

        @PostMapping
        public Map<String, String> post() {
            return Map.of("status", "ok");
        }
    }
}
