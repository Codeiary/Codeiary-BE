package com.codeiary.domain.github.dto.response;

import com.codeiary.domain.github.entity.GitHubConnection;

public record GitHubRepositoryResponse(Long installationId, Long id, String owner, String name, String branch) {
    public static GitHubRepositoryResponse from(GitHubConnection connection) {
        if (connection.getRepositoryId() == null) return null;
        return new GitHubRepositoryResponse(connection.getInstallationId(), connection.getRepositoryId(),
                connection.getRepositoryOwner(), connection.getRepositoryName(), connection.getBranch());
    }
}
