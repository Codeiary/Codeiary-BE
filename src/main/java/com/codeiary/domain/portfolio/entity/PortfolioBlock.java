package com.codeiary.domain.portfolio.entity;

import com.codeiary.domain.portfolio.entity.enums.PortfolioBlockType;
import com.codeiary.global.entity.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portfolio_block", uniqueConstraints = @UniqueConstraint(
        name = "uk_portfolio_block_order", columnNames = {"portfolio_id", "sort_order"}))
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class PortfolioBlock extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PortfolioBlockType blockType;

    @Column(nullable = false)
    private int sortOrder;

    private Double x;

    private Double y;

    private Double widthPx;

    private Double heightPx;

    protected PortfolioBlock(Portfolio portfolio, PortfolioBlockType blockType, int sortOrder) {
        this.portfolio = portfolio;
        this.blockType = blockType;
        this.sortOrder = sortOrder;
    }
}
