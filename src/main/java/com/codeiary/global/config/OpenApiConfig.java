package com.codeiary.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI codeiaryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Codeiary API")
                .description("Codeiary 블로그 API 문서")
                .version("v1"));
    }
}
