package com.codeiary.domain.portfolio.dto;

import com.codeiary.domain.portfolio.dto.request.PortfolioRequest;
import com.codeiary.domain.portfolio.dto.response.PortfolioCreateResponse;
import com.codeiary.domain.portfolio.dto.response.PortfolioFontResponse;
import com.codeiary.domain.portfolio.dto.response.PortfolioListResponse;
import com.codeiary.domain.portfolio.dto.response.PortfolioPageResponse;
import com.codeiary.domain.portfolio.dto.response.PortfolioResponse;
import com.codeiary.domain.portfolio.entity.Font;
import com.codeiary.domain.portfolio.entity.Portfolio;
import com.codeiary.domain.portfolio.entity.PortfolioBlock;
import com.codeiary.domain.portfolio.entity.PortfolioImageBlock;
import com.codeiary.domain.portfolio.entity.PortfolioTextBlock;
import com.codeiary.domain.portfolio.entity.PortfolioTextStyle;
import com.codeiary.domain.portfolio.entity.PortfolioTitleStyle;
import com.codeiary.domain.portfolio.entity.enums.PortfolioBlockType;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.config.MapStructConfig;
import org.springframework.data.domain.Page;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface PortfolioMapper {
    @Mapping(target = "defaultFontCode", source = "defaultFont.code")
    PortfolioResponse toResponse(Portfolio portfolio);

    PortfolioListResponse toListResponse(Portfolio portfolio);

    default PortfolioPageResponse toPageResponse(Page<Portfolio> page) {
        return new PortfolioPageResponse(page.getContent().stream().map(this::toListResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
    PortfolioListResponse.Author toAuthor(User author);
    PortfolioFontResponse toFontResponse(Font font);
    PortfolioCreateResponse toCreateResponse(Portfolio portfolio);

    @Mapping(target = "fontCode", source = "font.code")
    PortfolioResponse.Style toStyle(PortfolioTextStyle style);

    @Mapping(target = "fontCode", source = "font.code")
    PortfolioResponse.Style toStyle(PortfolioTitleStyle style);

    @Mapping(target = "type", source = "blockType")
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "imageAlt", ignore = true)
    PortfolioResponse.Block toTextBlock(PortfolioTextBlock block);

    @Mapping(target = "type", source = "blockType")
    @Mapping(target = "content", ignore = true)
    @Mapping(target = "textAlign", ignore = true)
    @Mapping(target = "styles", expression = "java(java.util.List.of())")
    PortfolioResponse.Block toImageBlock(PortfolioImageBlock block);

    default PortfolioResponse.Block toBlock(PortfolioBlock block) {
        return block instanceof PortfolioTextBlock text ? toTextBlock(text) : toImageBlock((PortfolioImageBlock) block);
    }

    default Portfolio toEntity(PortfolioRequest request, User author, Map<Long, Font> fonts) {
        Portfolio portfolio = Portfolio.create(author, fonts.get(request.defaultFontId()), request.title(),
                request.titleAlign(), org.springframework.util.StringUtils.hasText(request.representativeImageUrl())
                        ? request.representativeImageUrl() : null, request.publicPortfolio());
        addContent(portfolio, request, fonts);
        return portfolio;
    }

    default void updateEntity(PortfolioRequest request, @MappingTarget Portfolio portfolio, Map<Long, Font> fonts) {
        portfolio.update(fonts.get(request.defaultFontId()), request.title(), request.titleAlign(),
                org.springframework.util.StringUtils.hasText(request.representativeImageUrl())
                        ? request.representativeImageUrl() : null, request.publicPortfolio());
        addContent(portfolio, request, fonts);
    }

    private void addContent(Portfolio portfolio, PortfolioRequest request, Map<Long, Font> fonts) {
        for (var style : request.titleStyles()) {
            PortfolioTitleStyle.create(portfolio, fonts.get(style.fontId()), style.startOffset(),
                    style.endOffset(), style.fontSize(), style.textColor());
        }
        for (int order = 0; order < request.blocks().size(); order++) {
            var block = request.blocks().get(order);
            if (block.type() == PortfolioBlockType.IMAGE) {
                PortfolioImageBlock.create(portfolio, block.imageUrl(), block.imageAlt(), order,
                        block.x(), block.y(), block.widthPx(), block.heightPx());
            } else {
                PortfolioTextBlock text = PortfolioTextBlock.create(portfolio, block.content(), block.textAlign(), order,
                        block.x(), block.y(), block.widthPx(), block.heightPx());
                for (var style : block.styles()) {
                    PortfolioTextStyle.create(text, fonts.get(style.fontId()), style.startOffset(),
                            style.endOffset(), style.fontSize(), style.textColor());
                }
            }
        }
    }
}
