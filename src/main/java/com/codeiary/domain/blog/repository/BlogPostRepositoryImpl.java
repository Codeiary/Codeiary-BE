package com.codeiary.domain.blog.repository;

import com.codeiary.domain.blog.dto.request.BlogPostSort;
import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.entity.QBlogPost;
import com.codeiary.domain.user.entity.QUser;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
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
    public Page<BlogPost> findPublicPosts(String search, String category, BlogPostSort sort, Pageable pageable) {
        BooleanBuilder condition = new BooleanBuilder(post.publicPost.isTrue());
        addFilters(condition, search, category);
        return fetchPage(condition, sort, pageable);
    }

    @Override
    public Page<BlogPost> findAuthorPosts(Long authorId, String search, String category, Pageable pageable) {
        BooleanBuilder condition = new BooleanBuilder(post.author.id.eq(authorId));
        addFilters(condition, search, category);
        return fetchPage(condition, BlogPostSort.LATEST, pageable);
    }

    private Page<BlogPost> fetchPage(BooleanBuilder condition, BlogPostSort sort, Pageable pageable) {
        List<BlogPost> content = queries.selectFrom(post)
                .join(post.author, author).fetchJoin()
                .where(condition)
                .orderBy(ordering(sort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        Long total = queries.select(post.count())
                .from(post)
                .where(condition)
                .fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private void addFilters(BooleanBuilder condition, String search, String category) {
        if (search != null && !search.isBlank()) {
            String keyword = search.trim().toLowerCase();
            condition.and(post.title.lower().contains(keyword)
                    .or(post.content.lower().contains(keyword)));
        }
        if (category != null && !category.isBlank()) {
            condition.and(post.category.eq(category.trim()));
        }
    }

    private OrderSpecifier<?>[] ordering(BlogPostSort sort) {
        OrderSpecifier<?> primary = sort == BlogPostSort.VIEWS
                ? post.viewCount.desc() : post.createdAt.desc();
        return new OrderSpecifier<?>[]{primary, post.createdAt.desc(), post.id.desc()};
    }
}
