package com.codeiary.domain.portfolio.service;

import com.codeiary.domain.image.validation.ImageUrlValidator;
import com.codeiary.domain.portfolio.dto.PortfolioMapper;
import com.codeiary.domain.portfolio.dto.request.PortfolioRequest;
import com.codeiary.domain.portfolio.dto.response.PortfolioCreateResponse;
import com.codeiary.domain.portfolio.dto.response.PortfolioResponse;
import com.codeiary.domain.portfolio.dto.response.PortfolioFontResponse;
import com.codeiary.domain.portfolio.entity.Font;
import com.codeiary.domain.portfolio.entity.Portfolio;
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
    private final PortfolioMapper mapper;

    @Transactional(readOnly = true)
    @LogExecution
    public PortfolioResponse get(Long id, User user) {
        Portfolio portfolio = findPortfolio(id);
        if (!portfolio.isPublicPortfolio() && !portfolio.isWrittenBy(user)) {
            throw new RestApiException(PortfolioErrorCode.PORTFOLIO_NOT_FOUND);
        }
        return mapper.toResponse(portfolio);
    }

    @Transactional(readOnly = true)
    public List<PortfolioFontResponse> getFonts() {
        return fonts.findAllByEnabledTrueOrderByIdAsc().stream().map(mapper::toFontResponse).toList();
    }

    @Transactional
    @LogExecution
    public PortfolioCreateResponse create(User user, PortfolioRequest request) {
        Map<Long, Font> availableFonts = resolveFonts(request);
        validateImages(request);
        return mapper.toCreateResponse(portfolios.save(mapper.toEntity(request, user, availableFonts)));
    }

    @Transactional
    @LogExecution
    public void update(Long id, User user, PortfolioRequest request) {
        Portfolio portfolio = findOwnedPortfolio(id, user);
        Map<Long, Font> availableFonts = resolveFonts(request);
        validateImages(request);
        mapper.updateEntity(request, portfolio, availableFonts);
    }

    @Transactional
    @LogExecution
    public void delete(Long id, User user) {
        portfolios.delete(findOwnedPortfolio(id, user));
    }

    private Portfolio findPortfolio(Long id) {
        return portfolios.findById(id)
                .orElseThrow(() -> new RestApiException(PortfolioErrorCode.PORTFOLIO_NOT_FOUND));
    }

    private Portfolio findOwnedPortfolio(Long id, User user) {
        Portfolio portfolio = findPortfolio(id);
        if (!portfolio.isWrittenBy(user)) {
            throw new RestApiException(portfolio.isPublicPortfolio()
                    ? PortfolioErrorCode.PORTFOLIO_ACCESS_DENIED : PortfolioErrorCode.PORTFOLIO_NOT_FOUND);
        }
        return portfolio;
    }

    private void validateImages(PortfolioRequest request) {
        if (StringUtils.hasText(request.representativeImageUrl())) {
            imageUrls.validate(request.representativeImageUrl());
        }
        request.blocks().stream().filter(block -> block.type() == PortfolioBlockType.IMAGE)
                .forEach(block -> imageUrls.validate(block.imageUrl()));
    }

    private Map<Long, Font> resolveFonts(PortfolioRequest request) {
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

}
