package com.codeiary.domain.portfolio.repository;

import com.codeiary.domain.portfolio.entity.Portfolio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PortfolioRepositoryCustom {
    Page<Portfolio> findPublicPortfolios(Long authorId, String search, Pageable pageable);
    Page<Portfolio> findAuthorPortfolios(Long authorId, String search, Pageable pageable);
}
