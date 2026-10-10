package com.codeiary.domain.blog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BlogPostRequest(
        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 200)
        String title,
        @NotBlank(message = "본문을 입력해 주세요.")
        String content,
        @NotBlank(message = "카테고리를 입력해 주세요.")
        @Size(max = 100)
        String category,
        @Size(max = 10, message = "태그는 최대 10개까지 입력해 주세요.")
        List<@NotBlank(message = "빈 태그는 사용할 수 없습니다.") @Size(max = 30) String> tags,
        @Size(max = 2048)
        String representativeImageUrl,
        @NotNull(message = "공개 여부를 선택해 주세요.")
        Boolean publicPost
) {
}
