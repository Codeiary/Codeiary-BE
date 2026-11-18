package com.codeiary.domain.portfolio.fixture;

import com.codeiary.domain.portfolio.dto.request.PortfolioBlockRequest;
import com.codeiary.domain.portfolio.dto.request.PortfolioRequest;
import com.codeiary.domain.portfolio.dto.request.PortfolioTextStyleRequest;
import com.codeiary.domain.portfolio.entity.Font;
import com.codeiary.domain.portfolio.entity.Portfolio;
import com.codeiary.domain.portfolio.entity.enums.PortfolioBlockType;
import com.codeiary.domain.portfolio.entity.enums.TextAlignment;
import com.codeiary.domain.user.entity.User;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

public final class PortfolioFixture {

  public static final String COVER_IMAGE_URL = "https://img.example.com/cover.png";
  public static final String BLOCK_IMAGE_URL = "https://img.example.com/block.png";

  private PortfolioFixture() {}

  public static Portfolio create(User user, Font font, String title, boolean publicPortfolio) {
    return Portfolio.create(user, font, title, TextAlignment.LEFT, null, publicPortfolio);
  }

  public static Font font(Long id) {
    Font font = Font.create("font-" + id, "글꼴 " + id, "sans-serif", null);
    ReflectionTestUtils.setField(font, "id", id);
    return font;
  }

  public static PortfolioRequest request() {
    return request(COVER_IMAGE_URL);
  }

  public static PortfolioRequest request(String representativeImageUrl) {
    return new PortfolioRequest(
        "새 제목",
        TextAlignment.LEFT,
        List.of(new PortfolioTextStyleRequest(0, 1, 2L, null, null)),
        1L,
        representativeImageUrl,
        true,
        List.of(
            new PortfolioBlockRequest(
                PortfolioBlockType.TEXT,
                "본문",
                TextAlignment.LEFT,
                null,
                null,
                0.0,
                0.0,
                600.0,
                80.0,
                List.of(new PortfolioTextStyleRequest(0, 1, 2L, null, null))),
            new PortfolioBlockRequest(
                PortfolioBlockType.IMAGE,
                null,
                null,
                BLOCK_IMAGE_URL,
                null,
                0.0,
                100.0,
                300.0,
                200.0,
                List.of())));
  }

  public static String validRequestJson(Long fontId) {
    return """
    {"title":"제목","defaultFontId":%d,"publicPortfolio":true,
     "blocks":[{"type":"TEXT","content":"본문","x":0,"y":0,"widthPx":600,"heightPx":80}]}
    """
        .formatted(fontId);
  }
}
