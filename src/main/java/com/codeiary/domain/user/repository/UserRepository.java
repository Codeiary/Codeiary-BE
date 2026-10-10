package com.codeiary.domain.user.repository;

import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.dto.response.UserNeighborhoodResponse;
import com.codeiary.domain.user.entity.enums.OAuthProvider;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query(value = """
            select new com.codeiary.domain.user.dto.response.UserNeighborhoodResponse(
                user.id, user.nickname, user.profileImageUrl, user.githubUrl, user.contactEmail,
                (select count(post) from BlogPost post where post.author = user and post.publicPost = true)
            )
            from User user
            where user.enabled = true and user.nickname is not null
            order by (select count(post) from BlogPost post where post.author = user and post.publicPost = true) desc,
                     user.id asc
            """,
            countQuery = "select count(user) from User user where user.enabled = true and user.nickname is not null")
    Page<UserNeighborhoodResponse> findNeighborhood(Pageable pageable);

    Optional<User> findByEmail(String email);

    Optional<User> findByOauthProviderAndOauthSubject(OAuthProvider oauthProvider, String oauthSubject);

    Optional<User> findByNicknameIgnoreCase(String nickname);

    boolean existsByNicknameIgnoreCaseAndIdNot(String nickname, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

}
