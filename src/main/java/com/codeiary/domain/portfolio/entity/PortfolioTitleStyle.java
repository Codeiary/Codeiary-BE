package com.codeiary.domain.portfolio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portfolio_title_style")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioTitleStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "font_id")
    private Font font;

    @Column(nullable = false)
    private int startOffset;

    @Column(nullable = false)
    private int endOffset;

    private Double fontSize;

    @Column(length = 7)
    private String textColor;

    @Builder(access = AccessLevel.PRIVATE)
    private PortfolioTitleStyle(Portfolio portfolio, Font font, int startOffset,
                                int endOffset, Double fontSize, String textColor) {
        this.portfolio = portfolio;
        this.font = font;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.fontSize = fontSize;
        this.textColor = textColor;
    }

    public static PortfolioTitleStyle create(Portfolio portfolio, Font font, int startOffset,
                                              int endOffset, Double fontSize, String textColor) {
        PortfolioTitleStyle style = PortfolioTitleStyle.builder()
                .portfolio(portfolio)
                .font(font)
                .startOffset(startOffset)
                .endOffset(endOffset)
                .fontSize(fontSize)
                .textColor(textColor)
                .build();
        portfolio.addTitleStyle(style);
        return style;
    }
}
