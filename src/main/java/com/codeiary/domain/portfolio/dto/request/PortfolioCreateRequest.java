package com.codeiary.domain.portfolio.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.codeiary.domain.portfolio.entity.enums.TextAlignment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PortfolioCreateRequest(
        @NotBlank @Size(max = 200) String title,
        TextAlignment titleAlign,
        List<@NotNull @Valid PortfolioTextStyleRequest> titleStyles,
        @NotNull @Positive Long defaultFontId,
        @Size(max = 2048) String representativeImageUrl,
        @NotNull Boolean publicPortfolio,
        @NotEmpty List<@NotNull @Valid PortfolioBlockRequest> blocks
) {
    public PortfolioCreateRequest {
        titleAlign = titleAlign == null ? TextAlignment.LEFT : titleAlign;
        titleStyles = titleStyles == null ? List.of() : titleStyles;
    }

    @JsonIgnore
    @AssertTrue(message = "제목 서식은 제목의 글자 수를 넘을 수 없습니다.")
    public boolean isTitleStyleRangeValid() {
        return title == null || titleStyles.stream()
                .allMatch(style -> style == null || style.endOffset() <= title.length());
    }
}
