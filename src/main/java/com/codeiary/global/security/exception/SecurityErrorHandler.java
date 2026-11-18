package com.codeiary.global.security.exception;

import com.codeiary.domain.github.exception.GitHubErrorCode;
import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.ErrorCode;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.exception.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityErrorHandler
    implements AuthenticationEntryPoint, AccessDeniedHandler, AuthenticationFailureHandler {

  private final ObjectMapper objectMapper;

  @Value("${oauth2.redirect-home}")
  private String redirectHome;

  @Override
  public void commence(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      AuthenticationException authenticationException)
      throws IOException {
    log.atWarn()
        .addKeyValue("status", SecurityErrorCode.UNAUTHORIZED.getHttpStatus().value())
        .addKeyValue("error_type", authenticationException.getClass().getSimpleName())
        .addKeyValue("error_message", SecurityErrorCode.UNAUTHORIZED.getMessage())
        .log("Request failed");
    write(httpServletResponse, SecurityErrorCode.UNAUTHORIZED);
  }

  @Override
  public void handle(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      AccessDeniedException accessDeniedException)
      throws IOException {
    log.atWarn()
        .addKeyValue("status", SecurityErrorCode.DENIED_ACCESS.getHttpStatus().value())
        .addKeyValue("error_type", accessDeniedException.getClass().getSimpleName())
        .addKeyValue("error_message", SecurityErrorCode.DENIED_ACCESS.getMessage())
        .log("Request failed");
    write(httpServletResponse, SecurityErrorCode.DENIED_ACCESS);
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      AuthenticationException authenticationException)
      throws IOException {
    if (authenticationException.getCause() instanceof InsufficientAuthenticationException) {
      write(httpServletResponse, SecurityErrorCode.UNAUTHORIZED);
    } else if (authenticationException.getCause() instanceof AccessDeniedException) {
      write(httpServletResponse, SecurityErrorCode.DENIED_ACCESS);
    } else {
      handleServerError(httpServletResponse, authenticationException);
    }
  }

  public void handleServerError(HttpServletResponse httpServletResponse, Exception exception)
      throws IOException {
    log.atError()
        .addKeyValue("status", CommonErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus().value())
        .addKeyValue("error_type", exception.getClass().getSimpleName())
        .addKeyValue("error_message", CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage())
        .setCause(exception)
        .log("Request failed");
    write(httpServletResponse, CommonErrorCode.INTERNAL_SERVER_ERROR);
  }

  public void handleOAuthFailure(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      Exception exception)
      throws IOException {
    boolean repositoryFailure =
        exception instanceof RestApiException error
            && error.getErrorCode() instanceof GitHubErrorCode code
            && code != GitHubErrorCode.INVALID_GITHUB_LINK
            && code != GitHubErrorCode.GITHUB_ACCOUNT_CONFLICT;
    String redirectUrl =
        UriComponentsBuilder.fromUriString(redirectHome)
            .queryParam("error", repositoryFailure ? "github_repository" : "oauth2")
            .build()
            .toUriString();
    httpServletResponse.sendRedirect(redirectUrl);
  }

  private void write(HttpServletResponse httpServletResponse, ErrorCode errorCode)
      throws IOException {
    httpServletResponse.setStatus(errorCode.getHttpStatus().value());
    httpServletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
    httpServletResponse.setCharacterEncoding("UTF-8");
    httpServletResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    objectMapper.writeValue(
        httpServletResponse.getOutputStream(),
        new ErrorResponse(errorCode.getMessage(), errorCode.name()));
  }
}
