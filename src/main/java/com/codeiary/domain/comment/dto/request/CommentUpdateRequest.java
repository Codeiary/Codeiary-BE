package com.codeiary.domain.comment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentUpdateRequest(@NotBlank @Size(max = 2000) String content) {
}
