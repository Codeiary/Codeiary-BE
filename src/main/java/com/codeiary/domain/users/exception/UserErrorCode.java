package com.codeiary.domain.users.exception;

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
    INVALID_PROFILE_IMAGE(HttpStatus.BAD_REQUEST, "올바른 JPEG 프로필 사진을 선택해 주세요."),
    PROFILE_IMAGE_UPLOAD_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "프로필 사진 저장소가 설정되지 않았습니다."),
    PROFILE_IMAGE_UPLOAD_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "사진을 저장하지 못했습니다. 다시 시도해 주세요."),
    OAUTH_ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "소셜 계정을 연결할 수 없습니다."),
    OAUTH_REGISTRATION_CONFLICT(HttpStatus.CONFLICT, "가입 요청이 중복되었습니다. 다시 로그인해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;
}
