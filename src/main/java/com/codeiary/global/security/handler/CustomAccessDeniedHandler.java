package com.codeiary.global.security.handler;

import java.io.IOException;

import org.springframework.security.web.access.AccessDeniedHandler;


import com.codeiary.global.exception.dto.response.ErrorResponse;
import com.codeiary.global.security.exception.SecurityErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;


    private void setUpResponse(
            HttpServletResponse response,
            SecurityErrorCode securityErrorCode
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = new ErrorResponse(
                securityErrorCode.getMessage(),
                securityErrorCode.getHttpStatus().name()
        );

        String jsonResponse = objectMapper.writeValueAsString(errorResponse);

        response.getWriter().write(jsonResponse);
    }

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			org.springframework.security.access.AccessDeniedException accessDeniedException)
			throws IOException, ServletException {
		setUpResponse(response, SecurityErrorCode.DENIED_ACCESS);

	}
}
