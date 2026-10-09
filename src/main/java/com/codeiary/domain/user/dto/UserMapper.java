package com.codeiary.domain.user.dto;

import com.codeiary.domain.user.dto.response.UserProfileResponse;
import com.codeiary.domain.user.dto.response.PublicUserProfileResponse;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.config.MapStructConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface UserMapper {

    UserProfileResponse toResponse(User user);

    PublicUserProfileResponse toPublicResponse(User user);
}
