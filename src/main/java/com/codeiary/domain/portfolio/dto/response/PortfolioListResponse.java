package com.codeiary.domain.portfolio.dto.response;

import java.time.LocalDateTime;

public record PortfolioListResponse(Long id, String title, Author author, boolean publicPortfolio,
                                    String representativeImageUrl, LocalDateTime createdAt) {
    public record Author(Long id, String nickname) {}
}
