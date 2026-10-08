package com.codeiary.domain.users.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OnboardingRequest(
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Pattern(regexp = "[가-힣A-Za-z0-9_]{2,20}", message = "닉네임은 한글, 영문, 숫자, 밑줄로 2~20자를 입력해 주세요.")
        String nickname
) {
}
