package com.codeiary.global.security.token.controller;

import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.token.dto.TokenPair;
import com.codeiary.global.security.token.service.TokenService;
import com.codeiary.global.security.token.service.TokenSessionService;
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
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class TokenController {

    private final TokenSessionService sessions;
    private final TokenService cookies;

    @Operation(summary = "토큰 재발급", description = "Refresh Token 쿠키를 교체하고 새 Access Token 쿠키를 발급합니다.")
    @PostMapping({"/reissue", "/refresh"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reissue(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            TokenPair pair = sessions.reissue(cookies.refreshToken(request).orElse(null));
            cookies.writeTokens(response, pair);
        } catch (RestApiException exception) {
            cookies.clearTokens(response);
            throw exception;
        }
    }

    @Operation(summary = "로그아웃", description = "현재 로그인의 토큰을 폐기하고 인증 쿠키를 삭제합니다.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        sessions.logout(cookies.refreshToken(request).orElse(null), cookies.accessToken(request).orElse(null));
        cookies.clearTokens(response);
    }
}
