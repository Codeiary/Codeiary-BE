package com.codeiary.domain.portfolio.dto.response;

import java.util.List;

public record PortfolioPageResponse(List<PortfolioListResponse> content, int page, int size,
                                    long totalElements, int totalPages, boolean first, boolean last) {
}
