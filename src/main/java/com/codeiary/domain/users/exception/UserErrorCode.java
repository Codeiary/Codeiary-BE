package com.codeiary.domain.users.exception;

import org.springframework.http.HttpStatus;

import com.codeiary.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;


@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    NOT_FOUND_USER(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
