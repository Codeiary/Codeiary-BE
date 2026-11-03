package com.codeiary.domain.portfolio.entity;

import com.codeiary.domain.portfolio.entity.enums.PortfolioBlockType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portfolio_image_block")
@PrimaryKeyJoinColumn(name = "block_id")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioImageBlock extends PortfolioBlock {

    @Column(nullable = false, length = 2048)
    private String imageUrl;

    @Column(length = 1000)
    private String imageAlt;

    @Builder(access = AccessLevel.PRIVATE)
    private PortfolioImageBlock(Portfolio portfolio, int sortOrder, String imageUrl, String imageAlt,
                                Double x, Double y, Double widthPx, Double heightPx) {
        super(portfolio, PortfolioBlockType.IMAGE, sortOrder, x, y, widthPx, heightPx);
        this.imageUrl = imageUrl;
        this.imageAlt = imageAlt;
    }

    public static PortfolioImageBlock create(Portfolio portfolio, String imageUrl,
                                             String imageAlt, int sortOrder,
                                             Double x, Double y, Double widthPx, Double heightPx) {
        PortfolioImageBlock block = PortfolioImageBlock.builder()
                .portfolio(portfolio)
                .sortOrder(sortOrder)
                .imageUrl(imageUrl)
                .imageAlt(imageAlt)
                .x(x)
                .y(y)
                .widthPx(widthPx)
                .heightPx(heightPx)
                .build();
        portfolio.addBlock(block);
        return block;
    }
}
