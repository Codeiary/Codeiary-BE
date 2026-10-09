package com.codeiary.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.codeiary.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;


@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    NOT_FOUND_USER(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "닉네임은 한글, 영문, 숫자, 밑줄로 2~20자를 입력해 주세요."),
    NICKNAME_TAKEN(HttpStatus.CONFLICT, "이미 사용 중인 닉네임이에요."),
    OAUTH_ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "소셜 계정을 연결할 수 없습니다."),
    OAUTH_REGISTRATION_CONFLICT(HttpStatus.CONFLICT, "가입 요청이 중복되었습니다. 다시 로그인해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;
}
