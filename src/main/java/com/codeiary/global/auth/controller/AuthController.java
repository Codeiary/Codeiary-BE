package com.codeiary.global.auth.controller;

import com.codeiary.global.auth.dto.request.LoginRequest;
import com.codeiary.global.auth.dto.request.RefreshTokenRequest;
import com.codeiary.global.auth.dto.response.TokenResponse;
import com.codeiary.global.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "사용자 로그인 및 토큰 갱신")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "사용자 로그인", description = "Access Token과 Refresh Token을 발급합니다.")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "Refresh Token은 한 번만 사용할 수 있고 갱신 시 교체됩니다.")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "같은 로그인에서 발급한 모든 Access Token과 Refresh Token을 즉시 차단합니다.")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
