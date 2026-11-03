package com.codeiary.domain.portfolio.controller;

import com.codeiary.domain.auth.provider.JwtTokenProvider;
import com.codeiary.domain.portfolio.entity.Font;
import com.codeiary.domain.portfolio.entity.Portfolio;
import com.codeiary.domain.portfolio.entity.PortfolioBlock;
import com.codeiary.domain.portfolio.entity.PortfolioImageBlock;
import com.codeiary.domain.portfolio.entity.PortfolioTextBlock;
import com.codeiary.domain.portfolio.entity.enums.PortfolioBlockType;
import com.codeiary.domain.portfolio.entity.enums.TextAlignment;
import com.codeiary.domain.portfolio.repository.FontRepository;
import com.codeiary.domain.portfolio.repository.PortfolioRepository;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class PortfolioControllerTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private FontRepository fonts;
    @Autowired private PortfolioRepository portfolios;
    @Autowired private JwtTokenProvider tokens;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("로그인한 사용자의 포트폴리오와 블록·좌표·정렬·서식을 함께 저장한다.")
    void createPortfolio() throws Exception {
        User author = users.saveAndFlush(UserFixture.create(Role.USER));
        Font defaultFont = fonts.saveAndFlush(Font.create("test-sans", "고딕", "sans-serif", null));
        Font titleFont = fonts.saveAndFlush(Font.create("test-serif", "명조", "serif", null));
        String request = """
                {
                  "title": "백엔드 개발자",
                  "titleAlign": "CENTER",
                  "titleStyles": [
                    {"startOffset":0,"endOffset":3,"fontId":%d,"fontSize":36.5,"textColor":"#303d35"},
                    {"startOffset":4,"endOffset":7,"textColor":"#112233"}
                  ],
                  "defaultFontId": %d,
                  "representativeImageUrl": "https://img.example.com/cover.png",
                  "publicPortfolio": false,
                  "blocks": [
                    {"type":"TEXT", "content":"## 소개\\n\\n**강조**", "textAlign":"RIGHT",
                     "x":24.5, "y":110, "widthPx":600, "heightPx":100,
                     "styles":[{"startOffset":0,"endOffset":2,"fontSize":16.25},
                               {"startOffset":1,"endOffset":3,"textColor":"#112233"}]},
                    {"type":"IMAGE", "imageUrl":"https://img.example.com/project.png", "imageAlt":"구성도",
                     "styles":[{"startOffset":0,"endOffset":1,"fontId":9223372036854775807}],
                     "x":40, "y":240, "widthPx":400.5, "heightPx":200.25}
                  ]
                }
                """.formatted(titleFont.getId(), defaultFont.getId());

        String response = mockMvc.perform(post("/portfolios").cookie(accessCookie(author))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/portfolios/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn().getResponse().getContentAsString();
        Long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();
        entityManager.flush();
        entityManager.clear();

        Portfolio saved = portfolios.findById(id).orElseThrow();
        assertThat(saved.getAuthor().getId()).isEqualTo(author.getId());
        assertThat(saved.getDefaultFont().getId()).isEqualTo(defaultFont.getId());
        assertThat(saved.getTitle()).isEqualTo("백엔드 개발자");
        assertThat(saved.getTitleAlign()).isEqualTo(TextAlignment.CENTER);
        assertThat(saved.getTitleStyles()).hasSize(2);
        assertThat(saved.getTitleStyles().getFirst().getPortfolio().getId()).isEqualTo(id);
        assertThat(saved.getTitleStyles().getFirst().getFont().getId()).isEqualTo(titleFont.getId());
        assertThat(saved.getTitleStyles().getFirst().getFontSize()).isEqualTo(36.5);
        assertThat(saved.getTitleStyles().getFirst().getTextColor()).isEqualTo("#303d35");
        assertThat(saved.getTitleStyles().get(1).getFont()).isNull();
        assertThat(saved.getTitleStyles().get(1).getTextColor()).isEqualTo("#112233");
        assertThat(saved.isPublicPortfolio()).isFalse();
        assertThat(saved.getRepresentativeImageUrl()).isEqualTo("https://img.example.com/cover.png");
        assertThat(saved.getBlocks()).extracting(PortfolioBlock::getBlockType)
                .containsExactly(PortfolioBlockType.TEXT, PortfolioBlockType.IMAGE);
        assertThat(saved.getBlocks()).extracting(PortfolioBlock::getSortOrder).containsExactly(0, 1);
        PortfolioTextBlock text = (PortfolioTextBlock) saved.getBlocks().get(0);
        assertThat(text.getContent()).isEqualTo("## 소개\n\n**강조**");
        assertThat(text.getTextAlign()).isEqualTo(TextAlignment.RIGHT);
        assertThat(text.getY()).isEqualTo(110.0);
        assertThat(text.getHeightPx()).isEqualTo(100.0);
        assertThat(text.getStyles().getFirst().getFont()).isNull();
        assertThat(text.getStyles().getFirst().getFontSize()).isEqualTo(16.25);
        assertThat(text.getStyles()).hasSize(2);
        assertThat(text.getStyles().get(1).getTextColor()).isEqualTo("#112233");
        PortfolioImageBlock image = (PortfolioImageBlock) saved.getBlocks().get(1);
        assertThat(image.getImageUrl()).isEqualTo("https://img.example.com/project.png");
        assertThat(image.getImageAlt()).isEqualTo("구성도");
        assertThat(image.getWidthPx()).isEqualTo(400.5);
        assertThat(image.getHeightPx()).isEqualTo(200.25);
    }

    @Test
    @DisplayName("비로그인 사용자와 온보딩을 완료하지 않은 사용자의 생성을 차단한다.")
    void rejectUnauthorizedCreate() throws Exception {
        mockMvc.perform(post("/portfolios").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        User pending = users.saveAndFlush(UserFixture.create(Role.PENDING));
        mockMvc.perform(post("/portfolios").cookie(accessCookie(pending))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        assertThat(portfolios.count()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidBlocks")
    @DisplayName("잘못된 제목·서식·글꼴·좌표 요청을 400으로 거절하고 저장하지 않는다.")
    void rejectInvalidPortfolio(String blocks) throws Exception {
        User author = users.saveAndFlush(UserFixture.create(Role.USER));
        Font font = fonts.saveAndFlush(Font.create("test-sans", "고딕", "sans-serif", null));
        mockMvc.perform(post("/portfolios").cookie(accessCookie(author)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"defaultFontId\":%d,\"publicPortfolio\":true,\"blocks\":%s}"
                                .formatted(font.getId(), blocks)))
                .andExpect(status().isBadRequest());
        assertThat(portfolios.count()).isZero();
    }

    private static Stream<String> invalidBlocks() {
        return Stream.of(
                """
                [{"type":"TITLE","content":"옛 제목 블록","x":0,"y":0,"widthPx":600,"heightPx":80}]
                """,
                """
                [{"type":"TEXT","content":"본문","x":0,"y":80,"widthPx":600,"heightPx":100,
                  "styles":[{"startOffset":0,"endOffset":3}]}]
                """,
                """
                [{"type":"TEXT","content":"본문","x":0,"y":0,"widthPx":600,"heightPx":100,
                  "styles":[{"startOffset":2,"endOffset":1}]}]
                """,
                """
                [{"type":"TEXT","content":"본문","x":0,"y":0,"widthPx":600,"heightPx":100,
                  "styles":[null]}]
                """,
                """
                [{"type":"TEXT","content":"본문","x":0,"y":0,"widthPx":600,"heightPx":80,
                  "styles":[{"startOffset":0,"endOffset":1,"fontId":9223372036854775807}]}]
                """,
                """
                [{"type":"TEXT","content":"본문","x":10}]
                """,
                "[null]"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "y", "widthPx", "heightPx"})
    @DisplayName("좌표·너비·높이 중 하나라도 누락하면 저장하지 않는다.")
    void rejectMissingLayout(String field) throws Exception {
        User author = users.saveAndFlush(UserFixture.create(Role.USER));
        Font font = fonts.saveAndFlush(Font.create("test-sans", "고딕", "sans-serif", null));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var block = mapper.createObjectNode()
                .put("type", "TEXT").put("content", "본문")
                .put("x", 0).put("y", 0).put("widthPx", 600).put("heightPx", 80);
        block.remove(field);
        var request = mapper.createObjectNode()
                .put("title", "제목").put("defaultFontId", font.getId()).put("publicPortfolio", true);
        request.putArray("blocks").add(block);

        mockMvc.perform(post("/portfolios").cookie(accessCookie(author)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        assertThat(portfolios.count()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("포트폴리오 제목이 없거나 공백이면 저장하지 않는다.")
    void rejectBlankTitle(String title) throws Exception {
        User author = users.saveAndFlush(UserFixture.create(Role.USER));
        Font font = fonts.saveAndFlush(Font.create("test-sans", "고딕", "sans-serif", null));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var request = mapper.readTree(validRequest(font.getId()));
        ((com.fasterxml.jackson.databind.node.ObjectNode) request).put("title", title);
        mockMvc.perform(post("/portfolios").cookie(accessCookie(author)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        assertThat(portfolios.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"startOffset\":0,\"endOffset\":3}]",
            "[{\"startOffset\":0,\"endOffset\":1,\"fontId\":9223372036854775807}]"
    })
    @DisplayName("제목 서식의 범위와 글꼴이 잘못되면 저장하지 않는다.")
    void rejectInvalidTitleStyle(String styles) throws Exception {
        User author = users.saveAndFlush(UserFixture.create(Role.USER));
        Font font = fonts.saveAndFlush(Font.create("test-sans", "고딕", "sans-serif", null));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(validRequest(font.getId()));
        request.set("titleStyles", mapper.readTree(styles));
        mockMvc.perform(post("/portfolios").cookie(accessCookie(author)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        assertThat(portfolios.count()).isZero();
    }

    @Test
    @DisplayName("텍스트 본문을 생략하면 빈 텍스트 블록으로 저장한다.")
    void createEmptyTextBlock() throws Exception {
        User author = users.saveAndFlush(UserFixture.create(Role.USER));
        Font font = fonts.saveAndFlush(Font.create("test-sans", "고딕", "sans-serif", null));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var request = mapper.readTree(validRequest(font.getId()));
        ((com.fasterxml.jackson.databind.node.ObjectNode) request.get("blocks").get(0)).remove("content");

        mockMvc.perform(post("/portfolios").cookie(accessCookie(author)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        entityManager.flush();
        entityManager.clear();
        Portfolio saved = portfolios.findAll().getFirst();
        assertThat(((PortfolioTextBlock) saved.getBlocks().getFirst()).getContent()).isEmpty();
    }

    private String validRequest(Long fontId) {
        return """
                {"title":"제목","defaultFontId":%d,"publicPortfolio":true,
                 "blocks":[{"type":"TEXT","content":"본문","x":0,"y":0,"widthPx":600,"heightPx":80}]}
                """.formatted(fontId);
    }

    private Cookie accessCookie(User user) {
        return new Cookie("access_token", tokens.generateTokenPair(
                user.getId(), user.getRole().name(), UUID.randomUUID()).accessToken());
    }
}
