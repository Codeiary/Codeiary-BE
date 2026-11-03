package com.codeiary.domain.portfolio.entity;

import com.codeiary.domain.portfolio.entity.enums.TextAlignment;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.entity.TimeBaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portfolio")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TextAlignment titleAlign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "default_font_id", nullable = false)
    private Font defaultFont;

    @Column(length = 2048)
    private String representativeImageUrl;

    @Column(name = "is_public", nullable = false)
    private boolean publicPortfolio;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PortfolioBlock> blocks = new ArrayList<>();

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startOffset ASC")
    private List<PortfolioTitleStyle> titleStyles = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    private Portfolio(User author, Font defaultFont, String title, TextAlignment titleAlign,
                       String representativeImageUrl, boolean publicPortfolio) {
        this.author = author;
        this.defaultFont = defaultFont;
        this.title = title;
        this.titleAlign = titleAlign;
        this.representativeImageUrl = representativeImageUrl;
        this.publicPortfolio = publicPortfolio;
    }

    public static Portfolio create(User author, Font defaultFont, String title, TextAlignment titleAlign,
                                   String representativeImageUrl, boolean publicPortfolio) {
        return Portfolio.builder()
                .author(author)
                .defaultFont(defaultFont)
                .title(title)
                .titleAlign(titleAlign)
                .representativeImageUrl(representativeImageUrl)
                .publicPortfolio(publicPortfolio)
                .build();
    }

    public List<PortfolioBlock> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    void addBlock(PortfolioBlock block) {
        blocks.add(block);
    }

    public List<PortfolioTitleStyle> getTitleStyles() {
        return Collections.unmodifiableList(titleStyles);
    }

    void addTitleStyle(PortfolioTitleStyle style) {
        titleStyles.add(style);
    }

    public boolean isWrittenBy(User user) {
        return user != null && author.getId().equals(user.getId());
    }
}
