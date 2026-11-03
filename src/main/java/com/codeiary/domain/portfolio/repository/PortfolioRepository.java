package com.codeiary.domain.portfolio.repository;

import com.codeiary.domain.portfolio.entity.Portfolio;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long>, PortfolioRepositoryCustom {
    @Override
    @EntityGraph(attributePaths = {"author", "defaultFont"})
    Optional<Portfolio> findById(Long id);
}
