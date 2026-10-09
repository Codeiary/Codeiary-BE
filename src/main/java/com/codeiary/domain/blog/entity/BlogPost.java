package com.codeiary.domain.blog.entity;

import com.codeiary.domain.user.entity.User;
import com.codeiary.global.entity.TimeBaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blog_post")
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BlogPostTag> postTags = new ArrayList<>();

    @Column(name = "representative_image_url", length = 2048)
    private String representativeImageUrl;

    @Column(name = "is_public", nullable = false)
    private boolean publicPost;

    @Column(nullable = false)
    private long viewCount;

    @Builder(access = AccessLevel.PRIVATE)
    private BlogPost(User author, String title, String content, Category category,
                     String representativeImageUrl, boolean publicPost) {
        this.author = Objects.requireNonNull(author);
        this.title = Objects.requireNonNull(title);
        this.content = Objects.requireNonNull(content);
        this.category = Objects.requireNonNull(category);
        this.representativeImageUrl = representativeImageUrl;
        this.publicPost = publicPost;
    }

    public static BlogPost create(User author, String title, String content, Category category,
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

    public void update(String title, String content, Category category,
                       String representativeImageUrl, boolean publicPost) {
        this.title = Objects.requireNonNull(title);
        this.content = Objects.requireNonNull(content);
        this.category = Objects.requireNonNull(category);
        this.representativeImageUrl = representativeImageUrl;
        this.publicPost = publicPost;
    }

    public void increaseViewCount() {
        viewCount++;
    }

    public void replaceTags(List<Tag> tags) {
        var names = tags.stream().map(Tag::getName).collect(Collectors.toSet());
        boolean changed = postTags.removeIf(link -> !names.contains(link.getTag().getName()));
        var existing = postTags.stream().map(link -> link.getTag().getName())
                .collect(Collectors.toSet());
        for (Tag tag : tags) {
            if (existing.add(tag.getName())) {
                postTags.add(BlogPostTag.create(this, tag));
                changed = true;
            }
        }
        if (changed) {
            markModified();
        }
    }

    public boolean isWrittenBy(User user) {
        return user != null && author.getId().equals(user.getId());
    }
}
