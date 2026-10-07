package com.codeiary.domain.users.controller;

import com.codeiary.domain.users.dto.response.UserProfileResponse;
import com.codeiary.domain.users.entity.User;
import com.codeiary.domain.users.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
