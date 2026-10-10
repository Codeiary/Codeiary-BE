package com.codeiary.domain.blog.repository;

import com.codeiary.domain.blog.entity.PostLike;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    @Query("select count(postLike) from PostLike postLike where postLike.post.id = :postId")
    long countForPost(@Param("postId") Long postId);

    @Query("select (count(postLike) > 0) from PostLike postLike "
            + "where postLike.post.id = :postId and postLike.user.id = :userId")
    boolean existsForUser(@Param("postId") Long postId, @Param("userId") Long userId);

    @Query("select postLike.post.id as postId, count(postLike) as likeCount "
            + "from PostLike postLike where postLike.post.id in :postIds group by postLike.post.id")
    List<LikeCount> countForPosts(@Param("postIds") Collection<Long> postIds);

    @Query("select postLike.post.id from PostLike postLike "
            + "where postLike.user.id = :userId and postLike.post.id in :postIds")
    Set<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);

    @Modifying
    @Query(value = "INSERT INTO post_like (post_id, user_id) VALUES (:postId, :userId) "
            + "ON CONFLICT (post_id, user_id) DO NOTHING", nativeQuery = true)
    void addLike(@Param("postId") Long postId, @Param("userId") Long userId);

    @Modifying
    @Query(value = "DELETE FROM post_like WHERE post_id = :postId AND user_id = :userId", nativeQuery = true)
    void removeLike(@Param("postId") Long postId, @Param("userId") Long userId);

    interface LikeCount {
        Long getPostId();
        Long getLikeCount();
    }
}
