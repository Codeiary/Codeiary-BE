package com.codeiary.domain.images.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ImagePresignRequest(
        @NotBlank(message = "이미지 형식을 입력해 주세요.") String contentType,
        @NotNull(message = "이미지 크기를 입력해 주세요.")
        @Positive(message = "이미지 크기는 양수여야 합니다.") Long contentLength
) {
}
