package com.codeiary.domain.blog.service;

import com.codeiary.domain.blog.dto.response.PostLikeResponse;
import com.codeiary.domain.blog.exception.BlogErrorCode;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.blog.repository.PostLikeRepository;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.exception.RestApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostLikeService {

    private final BlogPostRepository posts;
    private final PostLikeRepository postLikes;

    @Transactional
    public PostLikeResponse like(Long postId, User user) {
        requirePublicPost(postId);
        postLikes.addLike(postId, user.getId());
        return new PostLikeResponse(postLikes.countForPost(postId), true);
    }

    @Transactional
    public PostLikeResponse unlike(Long postId, User user) {
        requirePublicPost(postId);
        postLikes.removeLike(postId, user.getId());
        return new PostLikeResponse(postLikes.countForPost(postId), false);
    }

    private void requirePublicPost(Long postId) {
        if (!posts.existsByIdAndPublicPostTrue(postId)) {
            throw new RestApiException(BlogErrorCode.POST_NOT_FOUND);
        }
    }
}
