package com.codeiary.domain.github.entity;

import com.codeiary.domain.github.entity.enums.EventType;
import com.codeiary.domain.github.entity.enums.OutBoxStatus;
import com.codeiary.global.entity.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "github_blog_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GitHubBlogOutbox extends TimeBaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventType eventType;

    @Column(length = 200)
    private String title;

    @Column(length = 400)
    private String previousPath;

    @Column(length = 400)
    private String targetPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutBoxStatus status = OutBoxStatus.PENDING;

    @Column(nullable = false)
    private int claimVersion;

    @Column(nullable = false)
    private int failureCount;

    @Column(nullable = false, columnDefinition = "TIMESTAMP(6) WITH TIME ZONE")
    private Instant nextAttemptAt;

    @Builder(access = AccessLevel.PRIVATE)
    private GitHubBlogOutbox(Long postId, Long authorId, EventType eventType, String title,
            String previousPath, String targetPath, Instant nextAttemptAt) {
        this.postId = postId;
        this.authorId = authorId;
        this.eventType = eventType;
        this.title = title;
        this.previousPath = previousPath;
        this.targetPath = targetPath;
        this.nextAttemptAt = nextAttemptAt;
    }

    public static GitHubBlogOutbox create(Long postId, Long authorId, EventType eventType, String title,
            String previousPath, String targetPath) {
        return GitHubBlogOutbox.builder()
                .postId(postId)
                .authorId(authorId)
                .eventType(eventType)
                .title(title)
                .previousPath(previousPath)
                .targetPath(targetPath)
                .nextAttemptAt(Instant.now())
                .build();
    }
}
