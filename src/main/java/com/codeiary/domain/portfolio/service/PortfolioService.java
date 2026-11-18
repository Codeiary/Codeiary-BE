package com.codeiary.domain.portfolio.service;

import com.codeiary.domain.image.validation.ImageUrlValidator;
import com.codeiary.domain.portfolio.dto.PortfolioMapper;
import com.codeiary.domain.portfolio.dto.request.PortfolioRequest;
import com.codeiary.domain.portfolio.dto.response.PortfolioCreateResponse;
import com.codeiary.domain.portfolio.dto.response.PortfolioFontResponse;
import com.codeiary.domain.portfolio.dto.response.PortfolioResponse;
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

  private final PortfolioRepository portfolioRepository;
  private final FontRepository fontRepository;
  private final ImageUrlValidator imageUrlValidator;
  private final PortfolioMapper portfolioMapper;

  @Transactional(readOnly = true)
  @LogExecution
  public PortfolioResponse get(Long id, User user) {
    Portfolio portfolio = findPortfolio(id);
    if (!portfolio.isPublicPortfolio() && !portfolio.isWrittenBy(user)) {
      throw new RestApiException(PortfolioErrorCode.PORTFOLIO_NOT_FOUND);
    }
    return portfolioMapper.toResponse(portfolio);
  }

  @Transactional(readOnly = true)
  public List<PortfolioFontResponse> getFonts() {
    return fontRepository.findAllByEnabledTrueOrderByIdAsc().stream()
        .map(portfolioMapper::toFontResponse)
        .toList();
  }

  @Transactional
  @LogExecution
  public PortfolioCreateResponse create(User user, PortfolioRequest portfolioRequest) {
    Map<Long, Font> availableFonts = resolveFonts(portfolioRequest);
    validateImages(portfolioRequest);
    return portfolioMapper.toCreateResponse(
        portfolioRepository.save(portfolioMapper.toEntity(portfolioRequest, user, availableFonts)));
  }

  @Transactional
  @LogExecution
  public void update(Long id, User user, PortfolioRequest portfolioRequest) {
    Portfolio portfolio = findOwnedPortfolio(id, user);
    Map<Long, Font> availableFonts = resolveFonts(portfolioRequest);
    validateImages(portfolioRequest);
    portfolioMapper.updateEntity(portfolioRequest, portfolio, availableFonts);
  }

  @Transactional
  @LogExecution
  public void delete(Long id, User user) {
    portfolioRepository.delete(findOwnedPortfolio(id, user));
  }

  private Portfolio findPortfolio(Long id) {
    return portfolioRepository
        .findById(id)
        .orElseThrow(() -> new RestApiException(PortfolioErrorCode.PORTFOLIO_NOT_FOUND));
  }

  private Portfolio findOwnedPortfolio(Long id, User user) {
    Portfolio portfolio = findPortfolio(id);
    if (!portfolio.isWrittenBy(user)) {
      throw new RestApiException(
          portfolio.isPublicPortfolio()
              ? PortfolioErrorCode.PORTFOLIO_ACCESS_DENIED
              : PortfolioErrorCode.PORTFOLIO_NOT_FOUND);
    }
    return portfolio;
  }

  private void validateImages(PortfolioRequest portfolioRequest) {
    if (StringUtils.hasText(portfolioRequest.representativeImageUrl())) {
      imageUrlValidator.validate(portfolioRequest.representativeImageUrl());
    }
    portfolioRequest.blocks().stream()
        .filter(block -> block.type() == PortfolioBlockType.IMAGE)
        .forEach(block -> imageUrlValidator.validate(block.imageUrl()));
  }

  private Map<Long, Font> resolveFonts(PortfolioRequest portfolioRequest) {
    var ids = new HashSet<Long>();
    ids.add(portfolioRequest.defaultFontId());
    Stream.concat(
            portfolioRequest.titleStyles().stream(),
            portfolioRequest.blocks().stream()
                .filter(block -> block.type() == PortfolioBlockType.TEXT)
                .flatMap(block -> block.styles().stream()))
        .forEach(style -> {
          if (style.fontId() != null) {
            ids.add(style.fontId());
          }
        });
    Map<Long, Font> available =
        fontRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Font::getId, Function.identity()));
    if (available.size() != ids.size()
        || available.values().stream().anyMatch(font -> !font.isEnabled())) {
      throw new RestApiException(PortfolioErrorCode.FONT_NOT_AVAILABLE);
    }
    return available;
  }
}
