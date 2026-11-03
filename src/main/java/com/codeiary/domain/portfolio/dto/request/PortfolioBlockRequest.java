package com.codeiary.domain.portfolio.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.codeiary.domain.portfolio.entity.enums.PortfolioBlockType;
import com.codeiary.domain.portfolio.entity.enums.TextAlignment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PortfolioBlockRequest(
        @NotNull PortfolioBlockType type,
        String content,
        TextAlignment textAlign,
        @Size(max = 2048) String imageUrl,
        @Size(max = 1000) String imageAlt,
        @NotNull @PositiveOrZero Double x,
        @NotNull @PositiveOrZero Double y,
        @NotNull @Positive Double widthPx,
        @NotNull @Positive Double heightPx,
        List<@NotNull @Valid PortfolioTextStyleRequest> styles
) {
    public PortfolioBlockRequest {
        if (type == PortfolioBlockType.TEXT && content == null) {
            content = "";
        }
        textAlign = textAlign == null ? TextAlignment.LEFT : textAlign;
        styles = styles == null ? List.of() : styles;
    }

    @JsonIgnore
    @AssertTrue(message = "본문 서식은 본문의 글자 수를 넘을 수 없습니다.")
    public boolean isStyleRangeValid() {
        return content == null || styles.stream()
                .allMatch(style -> style == null || style.endOffset() <= content.length());
    }
}
