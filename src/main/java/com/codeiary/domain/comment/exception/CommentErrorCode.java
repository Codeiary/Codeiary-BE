package com.codeiary.domain.comment.exception;

import com.codeiary.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommentErrorCode implements ErrorCode {

    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    COMMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "작성자만 댓글을 변경할 수 있습니다."),
    COMMENT_REPLY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "답글을 작성할 수 없는 댓글입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
