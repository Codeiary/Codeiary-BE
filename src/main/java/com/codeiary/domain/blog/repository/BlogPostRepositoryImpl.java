package com.codeiary.domain.blog.repository;

import com.codeiary.domain.blog.dto.request.BlogPostSort;
import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.entity.QBlogPost;
import com.codeiary.domain.blog.entity.QBlogPostTag;
import com.codeiary.domain.blog.entity.QCategory;
import com.codeiary.domain.blog.entity.QTag;
import com.codeiary.domain.user.entity.QUser;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BlogPostRepositoryImpl implements BlogPostRepositoryCustom {

    private final JPAQueryFactory queries;
    private final QBlogPost post = QBlogPost.blogPost;
    private final QUser author = QUser.user;

    @Override
    public Page<BlogPost> findPublicPosts(String search, String category, String tag, BlogPostSort sort, Pageable pageable) {
        BooleanBuilder condition = new BooleanBuilder(post.publicPost.isTrue());
        addFilters(condition, search, category, tag);
        return fetchPage(condition, sort, pageable);
    }

    @Override
    public Page<BlogPost> findAuthorPosts(Long authorId, String search, String category, String tag, Pageable pageable) {
        BooleanBuilder condition = new BooleanBuilder(post.author.id.eq(authorId));
        addFilters(condition, search, category, tag);
        return fetchPage(condition, BlogPostSort.LATEST, pageable);
    }

    private Page<BlogPost> fetchPage(BooleanBuilder condition, BlogPostSort sort, Pageable pageable) {
        List<BlogPost> content = queries.selectFrom(post)
                .join(post.author, author).fetchJoin()
                .join(post.category, QCategory.category).fetchJoin()
                .where(condition)
                .orderBy(ordering(sort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        if (!content.isEmpty()) {
            QBlogPostTag postTag = QBlogPostTag.blogPostTag;
            queries.selectFrom(post)
                    .leftJoin(post.postTags, postTag).fetchJoin()
                    .leftJoin(postTag.tag, QTag.tag).fetchJoin()
                    .where(post.id.in(content.stream().map(BlogPost::getId).toList()))
                    .fetch();
        }
        Long total = queries.select(post.count())
                .from(post)
                .where(condition)
                .fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private void addFilters(BooleanBuilder condition, String search, String category, String tag) {
        if (search != null && !search.isBlank()) {
            String keyword = search.trim().toLowerCase(Locale.ROOT);
            condition.and(post.title.lower().contains(keyword)
                    .or(post.category.name.lower().contains(keyword))
                    .or(post.postTags.any().tag.name.lower().contains(keyword)));
        }
        if (category != null && !category.isBlank()) {
            condition.and(post.category.name.eq(category.trim()));
        }
        if (tag != null && !tag.isBlank()) {
            condition.and(post.postTags.any().tag.name.eq(tag.trim().toLowerCase(Locale.ROOT)));
        }
    }

    private OrderSpecifier<?>[] ordering(BlogPostSort sort) {
        return sort == BlogPostSort.LIKES
                ? new OrderSpecifier<?>[]{Expressions.numberTemplate(Long.class,
                        "(select count(pl) from PostLike pl where pl.post.id = {0})", post.id).desc(),
                        post.createdAt.desc(), post.id.desc()}
                : new OrderSpecifier<?>[]{post.createdAt.desc(), post.id.desc()};
    }
}
