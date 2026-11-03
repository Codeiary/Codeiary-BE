package com.codeiary.domain.portfolio.service;

import com.codeiary.domain.portfolio.dto.PortfolioMapper;
import com.codeiary.domain.portfolio.dto.response.PortfolioPageResponse;
import com.codeiary.domain.portfolio.repository.PortfolioRepository;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.logging.LogExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioListService {
    private static final int MAX_PAGE_SIZE = 50;
    private final PortfolioRepository portfolios;
    private final PortfolioMapper mapper;

    @LogExecution
    public PortfolioPageResponse getPublic(Long authorId, String search, int page, int size) {
        return mapper.toPageResponse(portfolios.findPublicPortfolios(authorId, search, pageable(page, size)));
    }

    @LogExecution
    public PortfolioPageResponse getMine(User user, String search, int page, int size) {
        return mapper.toPageResponse(portfolios.findAuthorPortfolios(user.getId(), search, pageable(page, size)));
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)));
    }
}
