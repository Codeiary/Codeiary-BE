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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    @DisplayName("수정은 ID·작성자를 유지하고 이전 블록·서식을 교체하며 삭제 시 자식만 함께 제거한다")
    void updateAndDeletePortfolio() throws Exception {
        User owner = users.saveAndFlush(UserFixture.create(Role.USER));
        Font font = fonts.findAll().getFirst();
        Portfolio portfolio = Portfolio.create(owner, font, "기존", TextAlignment.LEFT, "https://img.example.com/cover.png", false);
        var titleStyle = com.codeiary.domain.portfolio.entity.PortfolioTitleStyle.create(portfolio, font, 0, 2, 36.0, "#112233");
        PortfolioTextBlock text = PortfolioTextBlock.create(portfolio, "기존 본문", TextAlignment.LEFT, 0, 0.0, 0.0, 500.0, 100.0);
        var textStyle = com.codeiary.domain.portfolio.entity.PortfolioTextStyle.create(text, font, 0, 2, 20.0, "#112233");
        PortfolioImageBlock image = PortfolioImageBlock.create(portfolio, "https://img.example.com/old.png", "", 1, 0.0, 150.0, 300.0, 200.0);
        Long id = portfolios.saveAndFlush(portfolio).getId();
        Long oldText = text.getId(), oldImage = image.getId(), oldTitleStyle = titleStyle.getId(), oldTextStyle = textStyle.getId();
        entityManager.clear();
        var createdAt = portfolios.findById(id).orElseThrow().getCreatedAt();
        String request = """
                {"title":"수정된 제목","titleAlign":"RIGHT","defaultFontId":%d,"publicPortfolio":true,
                 "titleStyles":[{"startOffset":0,"endOffset":2,"fontSize":40}],
                 "blocks":[
                   {"type":"IMAGE","imageUrl":"https://img.example.com/new.png","x":10,"y":20,"widthPx":200,"heightPx":100},
                   {"type":"TEXT","content":"새 본문","textAlign":"CENTER","x":20,"y":150,"widthPx":500,"heightPx":80,
                    "styles":[{"startOffset":0,"endOffset":1,"fontSize":24,"textColor":"#336699"}]}]}
                """.formatted(font.getId());
        mockMvc.perform(put("/portfolios/{id}", id).cookie(accessCookie(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.createNativeQuery("SET CONSTRAINTS ALL IMMEDIATE").executeUpdate();
        entityManager.clear();
        Portfolio updated = portfolios.findById(id).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("수정된 제목");
        assertThat(updated.getAuthor().getId()).isEqualTo(owner.getId());
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.isPublicPortfolio()).isTrue();
        assertThat(updated.getRepresentativeImageUrl()).isNull();
        assertThat(updated.getTitleStyles()).hasSize(1);
        assertThat(updated.getBlocks()).extracting(PortfolioBlock::getBlockType)
                .containsExactly(PortfolioBlockType.IMAGE, PortfolioBlockType.TEXT);
        assertThat(((PortfolioTextBlock) updated.getBlocks().get(1)).getStyles().getFirst().getFontSize()).isEqualTo(24.0);
        assertThat(entityManager.find(PortfolioBlock.class, oldText)).isNull();
        assertThat(entityManager.find(PortfolioBlock.class, oldImage)).isNull();
        assertThat(entityManager.find(com.codeiary.domain.portfolio.entity.PortfolioTitleStyle.class, oldTitleStyle)).isNull();
        assertThat(entityManager.find(com.codeiary.domain.portfolio.entity.PortfolioTextStyle.class, oldTextStyle)).isNull();
        var blockIds = updated.getBlocks().stream().map(PortfolioBlock::getId).toList();
        Long newTitleStyle = updated.getTitleStyles().getFirst().getId();
        Long newTextStyle = ((PortfolioTextBlock) updated.getBlocks().get(1)).getStyles().getFirst().getId();
        mockMvc.perform(delete("/portfolios/{id}", id).cookie(accessCookie(owner))).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        assertThat(portfolios.existsById(id)).isFalse();
        blockIds.forEach(blockId -> assertThat(entityManager.find(PortfolioBlock.class, blockId)).isNull());
        assertThat(entityManager.find(com.codeiary.domain.portfolio.entity.PortfolioTitleStyle.class, newTitleStyle)).isNull();
        assertThat(entityManager.find(com.codeiary.domain.portfolio.entity.PortfolioTextStyle.class, newTextStyle)).isNull();
        assertThat(fonts.existsById(font.getId())).isTrue();
        assertThat(users.existsById(owner.getId())).isTrue();
    }

    @Test
    @DisplayName("미인증·타인·잘못된 수정 요청을 거절하고 기존 문서를 유지한다")
    void rejectUnauthorizedAndInvalidMutations() throws Exception {
        User owner = users.saveAndFlush(UserFixture.create(Role.USER));
        User other = users.saveAndFlush(User.create("other@example.com", "다른사용자", Role.USER));
        Font font = fonts.findAll().getFirst();
        Long publicId = portfolios.saveAndFlush(Portfolio.create(owner, font, "공개 원본", TextAlignment.LEFT, null, true)).getId();
        Long privateId = portfolios.saveAndFlush(Portfolio.create(owner, font, "비공개 원본", TextAlignment.LEFT, null, false)).getId();
        String request = validRequest(font.getId());
        mockMvc.perform(put("/portfolios/{id}", publicId).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/portfolios/{id}", publicId)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/portfolios/{id}", publicId).cookie(accessCookie(other)).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/portfolios/{id}", publicId).cookie(accessCookie(other))).andExpect(status().isForbidden());
        mockMvc.perform(put("/portfolios/{id}", privateId).cookie(accessCookie(other)).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/portfolios/{id}", privateId).cookie(accessCookie(other))).andExpect(status().isNotFound());
        mockMvc.perform(put("/portfolios/{id}", publicId).cookie(accessCookie(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("\"widthPx\":600", "\"widthPx\":0"))).andExpect(status().isBadRequest());
        mockMvc.perform(put("/portfolios/{id}", publicId).cookie(accessCookie(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(Long.MAX_VALUE))).andExpect(status().isBadRequest());
        assertThat(portfolios.findById(publicId).orElseThrow().getTitle()).isEqualTo("공개 원본");
        assertThat(portfolios.existsById(privateId)).isTrue();
    }

    @Test
    @DisplayName("상세 조회는 서식과 블록을 복원하고 비공개 문서는 작성자에게만 공개한다")
    void readDetailAndProtectPrivatePortfolio() throws Exception {
        User owner = users.saveAndFlush(UserFixture.create(Role.USER));
        User other = users.saveAndFlush(User.create("other@example.com", "다른사용자", Role.USER));
        Font font = fonts.findAll().getFirst();
        Portfolio portfolio = Portfolio.create(owner, font, "소개", TextAlignment.CENTER, null, false);
        com.codeiary.domain.portfolio.entity.PortfolioTitleStyle.create(portfolio, font, 0, 2, 36.0, "#112233");
        PortfolioTextBlock text = PortfolioTextBlock.create(portfolio, "**본문**", TextAlignment.RIGHT, 0, 10.0, 20.0, 500.0, 100.0);
        com.codeiary.domain.portfolio.entity.PortfolioTextStyle.create(text, font, 2, 4, 22.0, "#336699");
        PortfolioImageBlock.create(portfolio, "https://img.example.com/image.png", "그림", 1, 0.0, 150.0, 400.0, 200.0);
        Long id = portfolios.saveAndFlush(portfolio).getId();
        entityManager.clear();
        mockMvc.perform(get("/portfolios/{id}", id).cookie(accessCookie(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("소개"))
                .andExpect(jsonPath("$.titleStyles[0].fontCode").value(font.getCode()))
                .andExpect(jsonPath("$.blocks[0].content").value("**본문**"))
                .andExpect(jsonPath("$.blocks[0].styles[0].fontSize").value(22.0))
                .andExpect(jsonPath("$.blocks[0].widthPx").value(500.0))
                .andExpect(jsonPath("$.blocks[1].type").value("IMAGE"));
        mockMvc.perform(get("/portfolios/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/portfolios/{id}", id).cookie(accessCookie(other))).andExpect(status().isNotFound());
        mockMvc.perform(get("/portfolios/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
        Portfolio published = portfolios.saveAndFlush(Portfolio.create(owner, font, "공개", TextAlignment.LEFT, null, true));
        mockMvc.perform(get("/portfolios/{id}", published.getId())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("공개 목록과 내 목록을 분리하고 제목 검색 및 최신순 페이지네이션을 제공한다")
    void listPublicAndMine() throws Exception {
        User owner = users.saveAndFlush(UserFixture.create(Role.USER));
        User other = users.saveAndFlush(User.create("other@example.com", "다른사용자", Role.USER));
        Font font = fonts.findAll().getFirst();
        Portfolio first = portfolios.saveAndFlush(Portfolio.create(owner, font, "개발 100%", TextAlignment.LEFT, null, true));
        Portfolio second = portfolios.saveAndFlush(Portfolio.create(owner, font, "개발 두번째", TextAlignment.LEFT, null, true));
        Portfolio hidden = portfolios.saveAndFlush(Portfolio.create(owner, font, "개발 비공개", TextAlignment.LEFT, null, false));
        portfolios.saveAndFlush(Portfolio.create(other, font, "다른 작성자", TextAlignment.LEFT, null, false));
        entityManager.clear();
        mockMvc.perform(get("/portfolios").param("size", "1").param("search", "개발"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(second.getId()))
                .andExpect(jsonPath("$.content[0].blocks").doesNotExist());
        mockMvc.perform(get("/portfolios").param("size", "1").param("page", "1").param("search", "개발"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(first.getId()));
        mockMvc.perform(get("/portfolios").param("search", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/portfolios").param("authorId", other.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/portfolios/mine").cookie(accessCookie(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(hidden.getId()));
        mockMvc.perform(get("/portfolios/mine")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/portfolios/fonts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/portfolios").param("page", "-1").param("size", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(50));
    }

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
