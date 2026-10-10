package com.codeiary.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record UpdateProfileRequest(
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Pattern(regexp = "[가-힣A-Za-z0-9_]{2,20}", message = "닉네임은 한글, 영문, 숫자, 밑줄로 2~20자를 입력해 주세요.")
        String nickname,
        @URL(message = "올바른 프로필 이미지 주소를 입력해 주세요.")
        @Size(max = 2048)
        String profileImageUrl,
        @Pattern(regexp = "^(https://github[.]com/[A-Za-z0-9-]{1,39}/?)?$", message = "올바른 GitHub 프로필 주소를 입력해 주세요.")
        @Size(max = 255)
        String githubUrl,
        @Email(message = "올바른 연락 이메일을 입력해 주세요.")
        @Size(max = 254)
        String contactEmail
) {
}
