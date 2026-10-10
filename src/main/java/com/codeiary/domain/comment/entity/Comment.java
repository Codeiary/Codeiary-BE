package com.codeiary.domain.comment.entity;

import com.codeiary.domain.user.entity.User;
import com.codeiary.global.entity.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private CommentTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Comment(CommentTargetType targetType, Long targetId, User author, Comment parent, String content) {
        this.targetType = Objects.requireNonNull(targetType);
        this.targetId = Objects.requireNonNull(targetId);
        this.author = Objects.requireNonNull(author);
        this.parent = parent;
        this.content = Objects.requireNonNull(content);
    }

    public static Comment create(CommentTargetType targetType, Long targetId, User author,
                                 Comment parent, String content) {
        return Comment.builder()
                .targetType(targetType)
                .targetId(targetId)
                .author(author)
                .parent(parent)
                .content(content)
                .build();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isRoot() {
        return parent == null;
    }

    public boolean isWrittenBy(User user) {
        return user != null && author != null && author.getId().equals(user.getId());
    }

    public void update(String content) {
        this.content = Objects.requireNonNull(content);
        markModified();
    }

    public void delete() {
        if (isDeleted()) {
            return;
        }
        this.content = "";
        this.author = null;
        this.deletedAt = LocalDateTime.now();
        markModified();
    }
}
