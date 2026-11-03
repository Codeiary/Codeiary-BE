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
@Table(name = "portfolio_text_style")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioTextStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "text_block_id", nullable = false)
    private PortfolioTextBlock textBlock;

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
    private PortfolioTextStyle(PortfolioTextBlock textBlock, Font font, int startOffset,
                               int endOffset, Double fontSize, String textColor) {
        this.textBlock = textBlock;
        this.font = font;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.fontSize = fontSize;
        this.textColor = textColor;
    }

    public static PortfolioTextStyle create(PortfolioTextBlock textBlock, Font font,
                                             int startOffset, int endOffset,
                                             Double fontSize, String textColor) {
        PortfolioTextStyle style = PortfolioTextStyle.builder()
                .textBlock(textBlock)
                .font(font)
                .startOffset(startOffset)
                .endOffset(endOffset)
                .fontSize(fontSize)
                .textColor(textColor)
                .build();
        textBlock.addStyle(style);
        return style;
    }
}
