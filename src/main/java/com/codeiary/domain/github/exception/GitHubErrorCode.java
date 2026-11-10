package com.codeiary.domain.github.exception;

import com.codeiary.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GitHubErrorCode implements ErrorCode {
    INVALID_GITHUB_LINK(HttpStatus.BAD_REQUEST, "GitHub 연동을 다시 시작해 주세요."),
    GITHUB_ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "이미 다른 계정과 연결된 GitHub 계정입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
