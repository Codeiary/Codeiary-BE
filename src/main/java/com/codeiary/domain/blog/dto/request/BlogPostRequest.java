package com.codeiary.domain.blog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BlogPostRequest(
        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 200)
        String title,
        @NotBlank(message = "본문을 입력해 주세요.")
        String content,
        @Size(max = 100)
        String category,
        @Size(max = 2048)
        String representativeImageUrl,
        @NotNull(message = "공개 여부를 선택해 주세요.")
        Boolean publicPost
) {
}
