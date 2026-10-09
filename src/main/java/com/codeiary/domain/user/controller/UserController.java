package com.codeiary.domain.user.controller;

import com.codeiary.domain.user.dto.request.OnboardingRequest;
import com.codeiary.domain.user.dto.request.UpdateProfileRequest;
import com.codeiary.domain.user.dto.response.NicknameAvailabilityResponse;
import com.codeiary.domain.user.dto.response.UserProfileResponse;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "사용자")
@SecurityRequirement(name = "cookieAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "로그인한 사용자 조회")
    public UserProfileResponse me(@AuthenticationPrincipal User user) {
        return userService.getProfile(user);
    }

    @GetMapping("/nickname-availability")
    @Operation(summary = "닉네임 사용 가능 여부 확인", description = "현재 사용자의 닉네임은 중복에서 제외합니다.")
    public NicknameAvailabilityResponse nicknameAvailability(
            @AuthenticationPrincipal User user,
            @RequestParam String nickname
    ) {
        return userService.checkNickname(user.getId(), nickname);
    }

    @PostMapping(value = "/me/onboarding", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "온보딩 완료", description = "닉네임을 저장하고 가입 대기 계정을 일반 사용자로 전환합니다.")
    public UserProfileResponse completeOnboarding(
            @AuthenticationPrincipal User user,
            @Valid @ModelAttribute OnboardingRequest request
    ) {
        return userService.completeOnboarding(user.getId(), request);
    }

    @PutMapping("/me/profile")
    @Operation(summary = "내 프로필 수정", description = "닉네임과 프로필 이미지, GitHub, 공개 연락 이메일을 저장합니다. 비워 둔 선택 항목은 삭제합니다.")
    public UserProfileResponse updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return userService.updateProfile(user.getId(), request);
    }
}
