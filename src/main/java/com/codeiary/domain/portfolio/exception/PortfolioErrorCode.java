package com.codeiary.domain.portfolio.exception;

import com.codeiary.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PortfolioErrorCode implements ErrorCode {

    FONT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "사용할 수 없는 글꼴입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
