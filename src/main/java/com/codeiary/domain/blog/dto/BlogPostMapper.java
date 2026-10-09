package com.codeiary.domain.blog.dto;

import com.codeiary.domain.blog.dto.request.BlogPostRequest;
import com.codeiary.domain.blog.dto.response.BlogPostResponse;
import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.config.MapStructConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface BlogPostMapper {

    BlogPostResponse toResponse(BlogPost post);

    default BlogPost toEntity(BlogPostRequest request, User author) {
        return BlogPost.create(author, request.title(), request.content(), request.category(),
                request.representativeImageUrl(), request.publicPost());
    }
}
