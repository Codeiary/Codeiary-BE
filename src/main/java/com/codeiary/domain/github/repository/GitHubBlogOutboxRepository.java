package com.codeiary.domain.github.repository;

import com.codeiary.domain.github.entity.GitHubBlogOutbox;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubBlogOutboxRepository extends JpaRepository<GitHubBlogOutbox, Long>, GitHubBlogOutboxRepositoryCustom {
    Optional<GitHubBlogOutbox> findFirstByPostIdAndAuthorIdOrderByIdDesc(Long postId, Long authorId);
}
