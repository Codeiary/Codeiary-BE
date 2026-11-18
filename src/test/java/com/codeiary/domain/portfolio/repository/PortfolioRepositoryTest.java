package com.codeiary.domain.portfolio.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeiary.domain.portfolio.entity.Font;
import com.codeiary.domain.portfolio.entity.Portfolio;
import com.codeiary.domain.portfolio.fixture.PortfolioFixture;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.auditing.CurrentDateTimeProvider;
import org.springframework.data.domain.PageRequest;

class PortfolioRepositoryTest extends RepositoryTestSupport {

  @Autowired private PortfolioRepository portfolioRepository;
  @Autowired private FontRepository fontRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private AuditingHandler auditingHandler;

  @AfterEach
  void restoreClock() {
    auditingHandler.setDateTimeProvider(CurrentDateTimeProvider.INSTANCE);
  }

  @Test
  @DisplayName("공개 목록은 작성자와 제목으로 좁히고 비공개 문서는 내 목록에서만 조회할 수 있다.")
  void filterPublicAndAuthorPortfolios() {
    User author = userRepository.saveAndFlush(UserFixture.create());
    User other = userRepository.saveAndFlush(UserFixture.create("other@example.com"));
    Font font = fontRepository.findAll().getFirst();
    Portfolio publicPortfolio = save(author, font, "API_설계", true);
    Portfolio privatePortfolio = save(author, font, "API_비공개", false);
    save(other, font, "API_다른 사람", true);
    entityManager.clear();

    var publicPage =
        portfolioRepository.findPublicPortfolios(author.getId(), "  api_  ", PageRequest.of(0, 10));
    var myPage =
        portfolioRepository.findAuthorPortfolios(author.getId(), "api_", PageRequest.of(0, 10));

    assertThat(publicPage.getTotalElements()).isEqualTo(1);
    assertThat(publicPage.getContent())
        .extracting(Portfolio::getId)
        .containsExactly(publicPortfolio.getId());
    assertThat(myPage.getTotalElements()).isEqualTo(2);
    assertThat(myPage.getContent())
        .extracting(Portfolio::getId)
        .containsExactly(privatePortfolio.getId(), publicPortfolio.getId());
    assertThat(myPage.getContent().getFirst().getAuthor().getEmail()).isEqualTo(author.getEmail());
  }

  @Test
  @DisplayName("생성 시각이 같아도 ID 역순으로 페이지를 안정적으로 조회할 수 있다.")
  void paginateWithEqualCreatedAt() {
    auditingHandler.setDateTimeProvider(() -> Optional.of(LocalDateTime.of(2026, 11, 18, 12, 0)));
    User author = userRepository.saveAndFlush(UserFixture.create());
    Font font = fontRepository.findAll().getFirst();
    Portfolio first = save(author, font, "첫 포트폴리오", true);
    Portfolio second = save(author, font, "두 번째 포트폴리오", true);
    Portfolio third = save(author, font, "세 번째 포트폴리오", true);
    entityManager.clear();

    var firstPage = portfolioRepository.findPublicPortfolios(null, null, PageRequest.of(0, 2));
    var secondPage = portfolioRepository.findPublicPortfolios(null, null, PageRequest.of(1, 2));

    assertThat(firstPage.getTotalElements()).isEqualTo(3);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
    assertThat(firstPage.getContent())
        .extracting(Portfolio::getId)
        .containsExactly(third.getId(), second.getId());
    assertThat(secondPage.getContent()).extracting(Portfolio::getId).containsExactly(first.getId());
  }

  private Portfolio save(User author, Font font, String title, boolean publicPortfolio) {
    return portfolioRepository.saveAndFlush(
        PortfolioFixture.create(author, font, title, publicPortfolio));
  }
}
