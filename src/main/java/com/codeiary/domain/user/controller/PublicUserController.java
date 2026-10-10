package com.codeiary.domain.user.controller;

import com.codeiary.domain.user.dto.response.PublicUserProfileResponse;
import com.codeiary.domain.user.dto.response.UserNeighborhoodPageResponse;
import com.codeiary.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "공개 프로필")
public class PublicUserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    @Operation(summary = "공개 프로필 조회", description = "닉네임, 프로필 이미지와 사용자가 공개한 연락처를 조회합니다.")
    public PublicUserProfileResponse profile(@PathVariable Long userId) {
        return userService.getPublicProfile(userId);
    }

    @GetMapping("/by-nickname/{nickname}")
    @Operation(summary = "닉네임으로 공개 프로필 조회")
    public PublicUserProfileResponse profileByNickname(@PathVariable String nickname) {
        return userService.getPublicProfileByNickname(nickname);
    }

    @GetMapping("/neighborhood")
    @Operation(summary = "이웃 사용자 목록 조회", description = "온보딩을 마친 사용자의 집 정보를 공개 글 활동 순으로 조회합니다.")
    public UserNeighborhoodPageResponse neighborhood(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return userService.getNeighborhood(page, size);
    }
}
