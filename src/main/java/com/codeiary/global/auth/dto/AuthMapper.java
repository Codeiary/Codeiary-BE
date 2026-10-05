package com.codeiary.global.auth.dto;

import com.codeiary.domain.users.dto.UserMapper;
import com.codeiary.domain.users.entity.User;
import com.codeiary.global.auth.dto.response.TokenResponse;
import com.codeiary.global.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class, uses = UserMapper.class)
public interface AuthMapper {

    @Mapping(target = "user", source = "user")
    @Mapping(target = "tokenType", constant = "Bearer")
    TokenResponse toTokenResponse(User user, String accessToken, String refreshToken,
                                  long expiresIn, long refreshExpiresIn);
}
