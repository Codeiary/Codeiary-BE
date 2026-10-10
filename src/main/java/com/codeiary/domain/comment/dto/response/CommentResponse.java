package com.codeiary.domain.comment.dto.response;

import com.codeiary.domain.comment.entity.CommentTargetType;
import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        CommentTargetType targetType,
        Long targetId,
        Long parentId,
        CommentAuthorResponse author,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean deleted
) {
}
