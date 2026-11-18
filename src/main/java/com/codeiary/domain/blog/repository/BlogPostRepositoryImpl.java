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
  public Page<BlogPost> findPublicPosts(
      String search, String category, String tag, BlogPostSort blogPostSort, Pageable pageable) {
    BooleanBuilder booleanBuilder = new BooleanBuilder(post.publicPost.isTrue());
    addFilters(booleanBuilder, search, category, tag);
    return fetchPage(booleanBuilder, blogPostSort, pageable);
  }

  @Override
  public Page<BlogPost> findAuthorPosts(
      Long authorId, String search, String category, String tag, Pageable pageable) {
    BooleanBuilder booleanBuilder = new BooleanBuilder(post.author.id.eq(authorId));
    addFilters(booleanBuilder, search, category, tag);
    return fetchPage(booleanBuilder, BlogPostSort.LATEST, pageable);
  }

  private Page<BlogPost> fetchPage(
      BooleanBuilder booleanBuilder, BlogPostSort blogPostSort, Pageable pageable) {
    List<BlogPost> content =
        queries
            .selectFrom(post)
            .join(post.author, author)
            .fetchJoin()
            .join(post.category, QCategory.category)
            .fetchJoin()
            .where(booleanBuilder)
            .orderBy(ordering(blogPostSort))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
    if (!content.isEmpty()) {
      QBlogPostTag postTag = QBlogPostTag.blogPostTag;
      queries
          .selectFrom(post)
          .leftJoin(post.postTags, postTag)
          .fetchJoin()
          .leftJoin(postTag.tag, QTag.tag)
          .fetchJoin()
          .where(post.id.in(content.stream().map(BlogPost::getId).toList()))
          .fetch();
    }
    Long total = queries.select(post.count()).from(post).where(booleanBuilder).fetchOne();
    return new PageImpl<>(content, pageable, total == null ? 0 : total);
  }

  private void addFilters(
      BooleanBuilder booleanBuilder, String search, String category, String tag) {
    if (search != null && !search.isBlank()) {
      String keyword = search.trim().toLowerCase(Locale.ROOT);
      booleanBuilder.and(
          post.title
              .lower()
              .contains(keyword)
              .or(post.category.name.lower().contains(keyword))
              .or(post.postTags.any().tag.name.lower().contains(keyword)));
    }
    if (category != null && !category.isBlank()) {
      booleanBuilder.and(post.category.name.eq(category.trim()));
    }
    if (tag != null && !tag.isBlank()) {
      booleanBuilder.and(post.postTags.any().tag.name.eq(tag.trim().toLowerCase(Locale.ROOT)));
    }
  }

  private OrderSpecifier<?>[] ordering(BlogPostSort blogPostSort) {
    return blogPostSort == BlogPostSort.LIKES
        ? new OrderSpecifier<?>[] {
          Expressions.numberTemplate(
                  Long.class, "(select count(pl) from PostLike pl where pl.post.id = {0})", post.id)
              .desc(),
          post.createdAt.desc(),
          post.id.desc()
        }
        : new OrderSpecifier<?>[] {post.createdAt.desc(), post.id.desc()};
  }
}
