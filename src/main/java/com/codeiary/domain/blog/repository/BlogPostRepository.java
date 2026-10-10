package com.codeiary.domain.blog.repository;

import com.codeiary.domain.blog.entity.BlogPost;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long>, BlogPostRepositoryCustom {

    boolean existsByIdAndPublicPostTrue(Long id);

    @Override
    @EntityGraph(attributePaths = {"author", "category", "postTags.tag"})
    Optional<BlogPost> findById(Long id);

}
