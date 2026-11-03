package com.codeiary.domain.portfolio.dto.response;

import com.codeiary.domain.portfolio.entity.enums.PortfolioBlockType;
import com.codeiary.domain.portfolio.entity.enums.TextAlignment;
import java.time.LocalDateTime;
import java.util.List;

public record PortfolioResponse(Long id, String title, TextAlignment titleAlign,
        PortfolioListResponse.Author author, boolean publicPortfolio, String representativeImageUrl,
        String defaultFontCode, List<Style> titleStyles, List<Block> blocks, LocalDateTime createdAt) {
    public record Style(int startOffset, int endOffset, String fontCode, Double fontSize, String textColor) {
    }
    public record Block(Long id, PortfolioBlockType type, int sortOrder, Double x, Double y,
            Double widthPx, Double heightPx, String content, TextAlignment textAlign,
            String imageUrl, String imageAlt, List<Style> styles) {
    }
}
