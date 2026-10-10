package com.codeiary.domain.comment.dto;

import com.codeiary.domain.comment.dto.response.CommentAuthorResponse;
import com.codeiary.domain.comment.dto.response.CommentResponse;
import com.codeiary.domain.comment.entity.Comment;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface CommentMapper {

    @Mapping(target = "parentId", source = "parent.id")
    CommentResponse toResponse(Comment comment);

    default CommentAuthorResponse toAuthor(User user) {
        return user == null ? null
                : new CommentAuthorResponse(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }
}
