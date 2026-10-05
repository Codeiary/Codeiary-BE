package com.codeiary.global.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일을 입력해 주세요.")
        @Pattern(regexp = "[^\\p{Lu}\\p{Lt}]*", message = "이메일은 소문자로 입력해 주세요.")
        @Size(max = 254) String email,
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하여야 합니다.")
        @Pattern(regexp = "(?=.*[A-Za-z])(?=.*[0-9])(?=(?:.*\\p{Punct}){3}).*",
                message = "비밀번호는 영문과 숫자, 특수문자 3개 이상을 포함해야 합니다.") String password) {
}
