package com.codeiary.domain.github.exception;

import com.codeiary.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GitHubErrorCode implements ErrorCode {
    INVALID_GITHUB_LINK(HttpStatus.BAD_REQUEST, "GitHub 연동을 다시 시작해 주세요."),
    GITHUB_ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "이미 다른 계정과 연결된 GitHub 계정입니다."),
    GITHUB_REAUTH_REQUIRED(HttpStatus.CONFLICT, "GitHub 인증이 만료됐어요. 다시 인증해 주세요."),
    GITHUB_REPOSITORY_CREATION_FAILED(HttpStatus.CONFLICT, "CodeiaryBlog을 만들 수 없어요. 같은 이름의 저장소가 있다면 앱 접근 권한을 확인해 주세요."),
    GITHUB_APP_INSTALLATION_REQUIRED(HttpStatus.FORBIDDEN, "GitHub App 설치와 저장소 생성 권한 승인이 필요해요."),
    GITHUB_ACCESS_DENIED(HttpStatus.FORBIDDEN, "GitHub 접근이 제한됐어요. 앱 권한과 API 사용 한도를 확인해 주세요."),
    GITHUB_REPOSITORY_UNAVAILABLE(HttpStatus.FORBIDDEN, "연결할 수 없는 저장소입니다. 앱 설치와 쓰기 권한을 확인해 주세요."),
    GITHUB_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "GitHub 요청이 많아요. 잠시 후 다시 시도해 주세요."),
    GITHUB_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "GitHub에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;
}
