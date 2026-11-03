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
    private PortfolioTextBlock(Portfolio portfolio,
                               int sortOrder, String content, TextAlignment textAlign,
                               Double x, Double y, Double widthPx, Double heightPx) {
        super(portfolio, PortfolioBlockType.TEXT, sortOrder, x, y, widthPx, heightPx);
        this.content = content;
        this.textAlign = textAlign;
    }

    public static PortfolioTextBlock create(Portfolio portfolio,
                                            String content, TextAlignment textAlign, int sortOrder,
                                            Double x, Double y, Double widthPx, Double heightPx) {
        PortfolioTextBlock block = PortfolioTextBlock.builder()
                .portfolio(portfolio)
                .sortOrder(sortOrder)
                .content(content)
                .textAlign(textAlign)
                .x(x)
                .y(y)
                .widthPx(widthPx)
                .heightPx(heightPx)
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
