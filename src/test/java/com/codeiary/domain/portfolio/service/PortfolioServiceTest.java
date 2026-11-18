package com.codeiary.domain.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.codeiary.domain.image.validation.ImageUrlValidator;
import com.codeiary.domain.portfolio.dto.PortfolioMapper;
import com.codeiary.domain.portfolio.entity.Font;
import com.codeiary.domain.portfolio.entity.Portfolio;
import com.codeiary.domain.portfolio.exception.PortfolioErrorCode;
import com.codeiary.domain.portfolio.fixture.PortfolioFixture;
import com.codeiary.domain.portfolio.repository.FontRepository;
import com.codeiary.domain.portfolio.repository.PortfolioRepository;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.RestApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

  @Mock private PortfolioRepository portfolioRepository;
  @Mock private FontRepository fontRepository;
  @Mock private ImageUrlValidator imageUrlValidator;

  private PortfolioService portfolioService;

  @BeforeEach
  void setUp() {
    portfolioService =
        new PortfolioService(
            portfolioRepository,
            fontRepository,
            imageUrlValidator,
            Mappers.getMapper(PortfolioMapper.class));
  }

  @Test
  @DisplayName("기본·제목·본문 글꼴을 조회하고 두 이미지 주소를 검증한 뒤 저장할 수 있다.")
  void createWithFontsAndImages() {
    User user = UserFixture.createWithId();
    Font defaultFont = PortfolioFixture.font(1L);
    Font styleFont = PortfolioFixture.font(2L);
    given(fontRepository.findAllById(any())).willReturn(List.of(defaultFont, styleFont));
    given(portfolioRepository.save(any(Portfolio.class)))
        .willAnswer(invocation -> {
          Portfolio portfolio = invocation.getArgument(0);
          ReflectionTestUtils.setField(portfolio, "id", 10L);
          return portfolio;
        });

    var response = portfolioService.create(user, PortfolioFixture.request());

    assertThat(response.id()).isEqualTo(10L);
    then(fontRepository)
        .should()
        .findAllById(
            argThat(ids -> {
              var fontIds = new HashSet<Long>();
              ids.forEach(fontIds::add);
              return fontIds.equals(Set.of(1L, 2L));
            }));
    then(imageUrlValidator).should().validate(PortfolioFixture.COVER_IMAGE_URL);
    then(imageUrlValidator).should().validate(PortfolioFixture.BLOCK_IMAGE_URL);
  }

  @Test
  @DisplayName("비활성 글꼴을 요청하면 포트폴리오를 저장할 수 없다.")
  void rejectDisabledFont() {
    Font defaultFont = PortfolioFixture.font(1L);
    Font disabledFont = PortfolioFixture.font(2L);
    ReflectionTestUtils.setField(disabledFont, "enabled", false);
    given(fontRepository.findAllById(any())).willReturn(List.of(defaultFont, disabledFont));

    assertThatThrownBy(
            () -> portfolioService.create(UserFixture.createWithId(), PortfolioFixture.request()))
        .isInstanceOf(RestApiException.class)
        .extracting("errorCode")
        .isEqualTo(PortfolioErrorCode.FONT_NOT_AVAILABLE);
    then(portfolioRepository).should(never()).save(any());
    then(imageUrlValidator).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("수정할 이미지 주소가 잘못되면 기존 포트폴리오를 유지할 수 있다.")
  void rejectInvalidImageOnUpdate() {
    User user = UserFixture.createWithId();
    Font defaultFont = PortfolioFixture.font(1L);
    Font styleFont = PortfolioFixture.font(2L);
    Portfolio portfolio = PortfolioFixture.create(user, defaultFont, "기존 제목", false);
    given(portfolioRepository.findById(10L)).willReturn(Optional.of(portfolio));
    given(fontRepository.findAllById(any())).willReturn(List.of(defaultFont, styleFont));
    willThrow(new RestApiException(CommonErrorCode.INVALID_PARAMETER))
        .given(imageUrlValidator)
        .validate(PortfolioFixture.BLOCK_IMAGE_URL);

    assertThatThrownBy(() -> portfolioService.update(10L, user, PortfolioFixture.request(null)))
        .isInstanceOf(RestApiException.class)
        .extracting("errorCode")
        .isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    assertThat(portfolio.getTitle()).isEqualTo("기존 제목");
    assertThat(portfolio.getBlocks()).isEmpty();
  }
}
