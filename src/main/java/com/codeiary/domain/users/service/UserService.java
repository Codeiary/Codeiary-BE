package com.codeiary.domain.users.service;

import com.codeiary.domain.users.dto.UserMapper;
import com.codeiary.domain.users.dto.response.UserProfileResponse;
import com.codeiary.domain.users.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public UserProfileResponse getProfile(User user) {
        return userMapper.toResponse(user);
    }
}
