package com.codeiary.domain.auth.controller;

import com.codeiary.domain.auth.cookie.TokenCookieManager;
import com.codeiary.domain.auth.dto.TokenPair;
import com.codeiary.domain.auth.service.TokenService;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.logging.LogExecution;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@LogExecution
@RequestMapping("/auth")
@RequiredArgsConstructor
public class TokenController {

  private final TokenService tokenService;
  private final TokenCookieManager tokenCookieManager;

  @Operation(summary = "토큰 재발급", description = "Refresh Token 쿠키를 교체하고 새 Access Token 쿠키를 발급합니다.")
  @PostMapping("/reissue")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reissue(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
    httpServletResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    try {
      TokenPair tokenPair =
          tokenService.reissue(tokenCookieManager.refreshToken(httpServletRequest).orElse(null));
      tokenCookieManager.writeTokens(httpServletResponse, tokenPair);
    } catch (RestApiException exception) {
      tokenCookieManager.clearTokens(httpServletResponse);
      throw exception;
    }
  }

  @Operation(summary = "로그아웃", description = "현재 로그인의 토큰을 폐기하고 인증 쿠키를 삭제합니다.")
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
    httpServletResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    tokenService.logout(
        tokenCookieManager.refreshToken(httpServletRequest).orElse(null),
        tokenCookieManager.accessToken(httpServletRequest).orElse(null));
    tokenCookieManager.clearTokens(httpServletResponse);
  }
}
