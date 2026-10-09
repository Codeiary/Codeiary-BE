package com.codeiary.domain.image.exception;

import com.codeiary.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ImageErrorCode implements ErrorCode {
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "올바른 JPG 또는 PNG 이미지를 선택해 주세요."),
    IMAGE_UPLOAD_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "이미지 저장소가 설정되지 않았습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "이미지를 저장하지 못했습니다. 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;
}
