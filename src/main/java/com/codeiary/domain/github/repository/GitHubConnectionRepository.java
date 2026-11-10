package com.codeiary.domain.github.repository;

import com.codeiary.domain.github.entity.GitHubConnection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubConnectionRepository extends JpaRepository<GitHubConnection, Long> {
    Optional<GitHubConnection> findByUserId(Long userId);
}
