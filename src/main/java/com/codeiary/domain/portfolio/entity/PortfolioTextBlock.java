package com.codeiary.domain.portfolio.entity;

import com.codeiary.domain.portfolio.entity.enums.PortfolioBlockType;
import com.codeiary.domain.portfolio.entity.enums.TextAlignment;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portfolio_text_block")
@PrimaryKeyJoinColumn(name = "block_id")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioTextBlock extends PortfolioBlock {

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TextAlignment textAlign;

    @OneToMany(mappedBy = "textBlock", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startOffset ASC")
    private List<PortfolioTextStyle> styles = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    private PortfolioTextBlock(Portfolio portfolio, PortfolioBlockType blockType,
                               int sortOrder, String content, TextAlignment textAlign) {
        super(portfolio, blockType, sortOrder);
        this.content = content;
        this.textAlign = textAlign;
    }

    public static PortfolioTextBlock createText(Portfolio portfolio, String content, int sortOrder) {
        return create(portfolio, PortfolioBlockType.TEXT, content, sortOrder);
    }

    public static PortfolioTextBlock createTitle(Portfolio portfolio, String content, int sortOrder) {
        return create(portfolio, PortfolioBlockType.TITLE, content, sortOrder);
    }

    private static PortfolioTextBlock create(Portfolio portfolio, PortfolioBlockType type,
                                              String content, int sortOrder) {
        PortfolioTextBlock block = PortfolioTextBlock.builder()
                .portfolio(portfolio)
                .blockType(type)
                .sortOrder(sortOrder)
                .content(content)
                .textAlign(TextAlignment.LEFT)
                .build();
        portfolio.addBlock(block);
        return block;
    }

    public List<PortfolioTextStyle> getStyles() {
        return Collections.unmodifiableList(styles);
    }

    void addStyle(PortfolioTextStyle style) {
        styles.add(style);
    }
}
