package com.codeiary.global.security.exception;

import com.codeiary.global.exception.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        log.atWarn()
                .addKeyValue("status", SecurityErrorCode.UNAUTHORIZED.getHttpStatus().value())
                .addKeyValue("error_type", exception.getClass().getSimpleName())
                .addKeyValue("error_message", SecurityErrorCode.UNAUTHORIZED.getMessage())
                .log("Request failed");
        write(response, SecurityErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        log.atWarn()
                .addKeyValue("status", SecurityErrorCode.DENIED_ACCESS.getHttpStatus().value())
                .addKeyValue("error_type", exception.getClass().getSimpleName())
                .addKeyValue("error_message", SecurityErrorCode.DENIED_ACCESS.getMessage())
                .log("Request failed");
        write(response, SecurityErrorCode.DENIED_ACCESS);
    }

    private void write(HttpServletResponse response, SecurityErrorCode error) throws IOException {
        response.setStatus(error.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getOutputStream(),
                new ErrorResponse(error.getMessage(), error.name()));
    }
}
