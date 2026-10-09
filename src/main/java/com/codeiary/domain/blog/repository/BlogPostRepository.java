package com.codeiary.domain.blog.repository;

import com.codeiary.domain.blog.entity.BlogPost;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    @Override
    @EntityGraph(attributePaths = "author")
    Optional<BlogPost> findById(Long id);

    @EntityGraph(attributePaths = "author")
    @Query("""
            select p from BlogPost p
            where p.publicPost = true
              and (:search is null or lower(p.title) like lower(concat('%', :search, '%'))
                   or lower(p.content) like lower(concat('%', :search, '%')))
              and (:category is null or p.category = :category)
            """)
    Page<BlogPost> findPublicPosts(
            @Param("search") String search,
            @Param("category") String category,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "author")
    @Query("""
            select p from BlogPost p
            where p.author.id = :authorId
              and (:search is null or lower(p.title) like lower(concat('%', :search, '%'))
                   or lower(p.content) like lower(concat('%', :search, '%')))
              and (:category is null or p.category = :category)
            """ )
    Page<BlogPost> findAuthorPosts(
            @Param("authorId") Long authorId,
            @Param("search") String search,
            @Param("category") String category,
            Pageable pageable
    );
}
