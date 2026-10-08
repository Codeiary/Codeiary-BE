package com.codeiary.domain.auth.exception;

import com.codeiary.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TokenErrorCode implements ErrorCode {

    TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "인증 토큰이 없습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 인증 토큰입니다."),
    TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "폐기된 인증 토큰입니다. 다시 로그인해 주세요."),
    TOKEN_TYPE_MISMATCH(HttpStatus.UNAUTHORIZED, "토큰 용도가 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
