package com.codeiary.domain.portfolio.repository;

import com.codeiary.domain.portfolio.entity.Portfolio;
import com.codeiary.domain.portfolio.entity.QPortfolio;
import com.codeiary.domain.user.entity.QUser;
import com.querydsl.core.BooleanBuilder;
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
public class PortfolioRepositoryImpl implements PortfolioRepositoryCustom {
    private final JPAQueryFactory queries;
    private final QPortfolio portfolio = QPortfolio.portfolio;
    private final QUser author = QUser.user;

    @Override
    public Page<Portfolio> findPublicPortfolios(Long authorId, String search, Pageable pageable) {
        BooleanBuilder condition = new BooleanBuilder(portfolio.publicPortfolio.isTrue());
        if (authorId != null) condition.and(portfolio.author.id.eq(authorId));
        return fetchPage(condition, search, pageable);
    }

    @Override
    public Page<Portfolio> findAuthorPortfolios(Long authorId, String search, Pageable pageable) {
        return fetchPage(new BooleanBuilder(portfolio.author.id.eq(authorId)), search, pageable);
    }

    private Page<Portfolio> fetchPage(BooleanBuilder condition, String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            condition.and(portfolio.title.lower().contains(search.trim().toLowerCase(Locale.ROOT)));
        }
        List<Portfolio> content = queries.selectFrom(portfolio)
                .join(portfolio.author, author).fetchJoin()
                .where(condition)
                .orderBy(portfolio.createdAt.desc(), portfolio.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        Long total = queries.select(portfolio.count()).from(portfolio).where(condition).fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }
}
