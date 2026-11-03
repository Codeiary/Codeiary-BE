package com.codeiary.domain.portfolio.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record PortfolioTextStyleRequest(
        @PositiveOrZero int startOffset,
        @Positive int endOffset,
        @Positive Long fontId,
        @DecimalMin("10") @DecimalMax("96") Double fontSize,
        @Pattern(regexp = "#[0-9a-fA-F]{6}") String textColor
) {
    @JsonIgnore
    @AssertTrue(message = "서식 시작 위치는 끝 위치보다 작아야 합니다.")
    public boolean isRangeValid() {
        return startOffset < endOffset;
    }
}
