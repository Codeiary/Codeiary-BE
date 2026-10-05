package com.codeiary.global.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh Token을 입력해 주세요.")
        @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "Refresh Token 형식이 올바르지 않습니다.")
        String refreshToken) {
}
