package com.codeiary.domain.user.controller;

import com.codeiary.domain.user.dto.response.UserProfileResponse;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "관리자")
@SecurityRequirement(name = "cookieAuth")
public class AdminController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "로그인한 관리자 조회")
    public UserProfileResponse me(@AuthenticationPrincipal User user) {
        return userService.getProfile(user);
    }
}
