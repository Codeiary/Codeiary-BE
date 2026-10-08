package com.codeiary.domain.users.dto;

import com.codeiary.domain.users.dto.response.UserProfileResponse;
import com.codeiary.domain.users.dto.response.PublicUserProfileResponse;
import com.codeiary.domain.users.entity.User;
import com.codeiary.global.config.MapStructConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface UserMapper {

    UserProfileResponse toResponse(User user);

    PublicUserProfileResponse toPublicResponse(User user);
}
