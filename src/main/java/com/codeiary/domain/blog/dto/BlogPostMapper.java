package com.codeiary.domain.blog.dto;

import com.codeiary.domain.blog.dto.request.BlogPostRequest;
import com.codeiary.domain.blog.dto.response.BlogPostResponse;
import com.codeiary.domain.blog.dto.response.BlogPostListItemResponse;
import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.entity.BlogPostTag;
import com.codeiary.domain.blog.entity.Category;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface BlogPostMapper {

    @Mapping(target = "category", source = "category.name")
    @Mapping(target = "tags", source = "postTags")
    BlogPostResponse toResponse(BlogPost post);

    @Mapping(target = "category", source = "category.name")
    @Mapping(target = "tags", source = "postTags")
    BlogPostListItemResponse toListItemResponse(BlogPost post);

    default String toTagName(BlogPostTag postTag) {
        return postTag.getTag().getName();
    }

    default BlogPost toEntity(BlogPostRequest request, User author, Category category) {
        return BlogPost.create(author, request.title(), request.content(), category,
                request.representativeImageUrl(), request.publicPost());
    }
}
