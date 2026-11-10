package com.codeiary.global.security.exception;

import com.codeiary.global.exception.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler, AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    @Value("${oauth2.redirect-home}")
    private String redirectHome;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        write(response, SecurityErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(response, SecurityErrorCode.DENIED_ACCESS);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        if (exception.getCause() instanceof InsufficientAuthenticationException) {
            write(response, SecurityErrorCode.UNAUTHORIZED);
        } else if (exception.getCause() instanceof AccessDeniedException) {
            write(response, SecurityErrorCode.DENIED_ACCESS);
        } else {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    public void handleOAuthFailure(HttpServletRequest request, HttpServletResponse response,
            Exception exception) throws IOException {
        String redirectUrl = UriComponentsBuilder.fromUriString(redirectHome)
                .queryParam("error", "oauth2")
                .build()
                .toUriString();
        response.sendRedirect(redirectUrl);
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
