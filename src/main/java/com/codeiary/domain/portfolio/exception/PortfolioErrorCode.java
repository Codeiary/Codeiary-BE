package com.codeiary.domain.portfolio.exception;

import com.codeiary.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PortfolioErrorCode implements ErrorCode {

    PORTFOLIO_NOT_FOUND(HttpStatus.NOT_FOUND, "포트폴리오를 찾을 수 없습니다."),
    PORTFOLIO_ACCESS_DENIED(HttpStatus.FORBIDDEN, "내가 작성한 포트폴리오만 변경할 수 있습니다."),
    FONT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "사용할 수 없는 글꼴입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
