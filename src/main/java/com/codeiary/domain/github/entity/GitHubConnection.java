package com.codeiary.domain.github.entity;

import com.codeiary.domain.user.entity.User;
import com.codeiary.global.entity.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "github_connection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GitHubConnection extends TimeBaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, unique = true)
    private Long githubUserId;
    @Column(nullable = false, length = 39)
    private String githubLogin;
    private Long installationId;
    private Long repositoryId;
    @Column(length = 39)
    private String repositoryOwner;
    @Column(length = 100)
    private String repositoryName;
    @Column(length = 255)
    private String branch;

    @Builder(access = AccessLevel.PRIVATE)
    private GitHubConnection(User user, Long githubUserId) {
        this.user = user;
        this.githubUserId = githubUserId;
    }

    public static GitHubConnection create(User user, Long githubUserId) {
        return GitHubConnection.builder().user(user).githubUserId(githubUserId).build();
    }

    public void connect(String login) {
        githubLogin = login;
    }

    public void connectRepository(Long installationId, Long repositoryId, String owner, String name, String branch) {
        this.installationId = installationId;
        this.repositoryId = repositoryId;
        this.repositoryOwner = owner;
        this.repositoryName = name;
        this.branch = branch;
    }
}
