package com.codeiary.domain.portfolio.service;

import com.codeiary.domain.image.validation.ImageUrlValidator;
import com.codeiary.domain.portfolio.dto.request.PortfolioBlockRequest;
import com.codeiary.domain.portfolio.dto.request.PortfolioCreateRequest;
import com.codeiary.domain.portfolio.dto.request.PortfolioTextStyleRequest;
import com.codeiary.domain.portfolio.dto.response.PortfolioCreateResponse;
import com.codeiary.domain.portfolio.dto.response.PortfolioFontResponse;
import com.codeiary.domain.portfolio.entity.Font;
import com.codeiary.domain.portfolio.entity.Portfolio;
import com.codeiary.domain.portfolio.entity.PortfolioImageBlock;
import com.codeiary.domain.portfolio.entity.PortfolioTextBlock;
import com.codeiary.domain.portfolio.entity.PortfolioTextStyle;
import com.codeiary.domain.portfolio.entity.PortfolioTitleStyle;
import com.codeiary.domain.portfolio.entity.enums.PortfolioBlockType;
import com.codeiary.domain.portfolio.exception.PortfolioErrorCode;
import com.codeiary.domain.portfolio.repository.FontRepository;
import com.codeiary.domain.portfolio.repository.PortfolioRepository;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.logging.LogExecution;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolios;
    private final FontRepository fonts;
    private final ImageUrlValidator imageUrls;

    @Transactional(readOnly = true)
    public List<PortfolioFontResponse> getFonts() {
        return fonts.findAllByEnabledTrueOrderByIdAsc().stream()
                .map(font -> new PortfolioFontResponse(font.getId(), font.getCode())).toList();
    }

    @Transactional
    @LogExecution
    public PortfolioCreateResponse create(User user, PortfolioCreateRequest request) {
        Map<Long, Font> availableFonts = resolveFonts(request);
        String imageUrl = request.representativeImageUrl();
        if (StringUtils.hasText(imageUrl)) {
            imageUrls.validate(imageUrl);
        } else {
            imageUrl = null;
        }
        Portfolio portfolio = Portfolio.create(user, availableFonts.get(request.defaultFontId()),
                request.title(), request.titleAlign(), imageUrl, request.publicPortfolio());
        for (PortfolioTextStyleRequest style : request.titleStyles()) {
            PortfolioTitleStyle.create(portfolio, availableFonts.get(style.fontId()),
                    style.startOffset(), style.endOffset(), style.fontSize(), style.textColor());
        }
        for (int index = 0; index < request.blocks().size(); index++) {
            addBlock(portfolio, request.blocks().get(index), index, availableFonts);
        }
        return new PortfolioCreateResponse(portfolios.save(portfolio).getId());
    }

    private Map<Long, Font> resolveFonts(PortfolioCreateRequest request) {
        var ids = new HashSet<Long>();
        ids.add(request.defaultFontId());
        Stream.concat(request.titleStyles().stream(),
                request.blocks().stream().filter(block -> block.type() == PortfolioBlockType.TEXT)
                        .flatMap(block -> block.styles().stream()))
                .forEach(style -> {
                    if (style.fontId() != null) {
                        ids.add(style.fontId());
                    }
                });
        Map<Long, Font> available = fonts.findAllById(ids).stream()
                .collect(Collectors.toMap(Font::getId, Function.identity()));
        if (available.size() != ids.size() || available.values().stream().anyMatch(font -> !font.isEnabled())) {
            throw new RestApiException(PortfolioErrorCode.FONT_NOT_AVAILABLE);
        }
        return available;
    }

    private void addBlock(Portfolio portfolio, PortfolioBlockRequest request, int order, Map<Long, Font> availableFonts) {
        if (request.type() == PortfolioBlockType.IMAGE) {
            imageUrls.validate(request.imageUrl());
            PortfolioImageBlock.create(portfolio, request.imageUrl(), request.imageAlt(), order,
                    request.x(), request.y(), request.widthPx(), request.heightPx());
            return;
        }
        PortfolioTextBlock text = PortfolioTextBlock.create(portfolio, request.content(),
                request.textAlign(), order,
                request.x(), request.y(), request.widthPx(), request.heightPx());
        addStyles(text, request.styles(), availableFonts);
    }

    private void addStyles(PortfolioTextBlock text, List<PortfolioTextStyleRequest> styles, Map<Long, Font> availableFonts) {
        for (PortfolioTextStyleRequest style : styles) {
            PortfolioTextStyle.create(text, availableFonts.get(style.fontId()), style.startOffset(),
                    style.endOffset(), style.fontSize(), style.textColor());
        }
    }
}
