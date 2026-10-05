package com.codeiary.global.exception;

import java.util.Objects;
import lombok.Getter;

@Getter
public class RestApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public RestApiException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode").getMessage());
        this.errorCode = errorCode;
    }

    public RestApiException(ErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode").getMessage(), cause);
        this.errorCode = errorCode;
    }
}
