package com.codeiary.domain.blog.entity;

import com.codeiary.domain.users.entity.User;
import com.codeiary.global.entity.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blog_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlogPost extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 100)
    private String category;

    @Column(name = "representative_image_url", length = 2048)
    private String representativeImageUrl;

    @Column(name = "is_public", nullable = false)
    private boolean publicPost;

    @Column(nullable = false)
    private long viewCount;

    @Builder(access = AccessLevel.PRIVATE)
    private BlogPost(User author, String title, String content, String category,
                     String representativeImageUrl, boolean publicPost) {
        this.author = Objects.requireNonNull(author);
        this.title = Objects.requireNonNull(title);
        this.content = Objects.requireNonNull(content);
        this.category = category;
        this.representativeImageUrl = representativeImageUrl;
        this.publicPost = publicPost;
    }

    public static BlogPost create(User author, String title, String content, String category,
                                  String representativeImageUrl, boolean publicPost) {
        return BlogPost.builder()
                .author(author)
                .title(title)
                .content(content)
                .category(category)
                .representativeImageUrl(representativeImageUrl)
                .publicPost(publicPost)
                .build();
    }

    public void update(String title, String content, String category,
                       String representativeImageUrl, boolean publicPost) {
        this.title = Objects.requireNonNull(title);
        this.content = Objects.requireNonNull(content);
        this.category = category;
        this.representativeImageUrl = representativeImageUrl;
        this.publicPost = publicPost;
    }

    public void increaseViewCount() {
        viewCount++;
    }
}
