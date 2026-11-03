package com.codeiary.domain.portfolio.controller;

import com.codeiary.domain.portfolio.dto.request.PortfolioRequest;
import com.codeiary.domain.portfolio.dto.response.PortfolioCreateResponse;
import com.codeiary.domain.portfolio.dto.response.PortfolioFontResponse;
import java.util.List;
import com.codeiary.domain.portfolio.dto.response.PortfolioResponse;
import com.codeiary.domain.portfolio.dto.response.PortfolioPageResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import com.codeiary.domain.portfolio.service.PortfolioService;
import com.codeiary.domain.portfolio.service.PortfolioListService;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.logging.LogExecution;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@LogExecution
@RequiredArgsConstructor
@RequestMapping("/portfolios")
@Tag(name = "포트폴리오")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioListService portfolioListService;

    @GetMapping
    @Operation(summary = "공개 포트폴리오 목록", description = "제목 검색과 최신순 페이지네이션을 지원합니다.")
    public PortfolioPageResponse list(@RequestParam(required = false) String search,
            @RequestParam(required = false) Long authorId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "6") int size) {
        return portfolioListService.getPublic(authorId, search, page, size);
    }

    @GetMapping("/mine")
    @SecurityRequirement(name = "cookieAuth")
    @Operation(summary = "내 포트폴리오 목록", description = "공개·비공개 문서를 최신순으로 조회합니다.")
    public PortfolioPageResponse mine(@AuthenticationPrincipal User user,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "6") int size) {
        return portfolioListService.getMine(user, search, page, size);
    }

    @GetMapping("/{portfolioId}")
    @Operation(summary = "포트폴리오 상세 조회", description = "공개 문서는 누구나, 비공개 문서는 작성자만 조회합니다.")
    public PortfolioResponse get(@PathVariable Long portfolioId, @AuthenticationPrincipal User user) {
        return portfolioService.get(portfolioId, user);
    }

    @GetMapping("/fonts")
    @SecurityRequirement(name = "cookieAuth")
    @Operation(summary = "사용 가능한 포트폴리오 글꼴 조회")
    public List<PortfolioFontResponse> fonts() {
        return portfolioService.getFonts();
    }

    @PostMapping
    @SecurityRequirement(name = "cookieAuth")
    @Operation(summary = "포트폴리오 생성", description = "제목과 제목 서식, 본문·이미지 블록을 함께 저장합니다. 글꼴은 등록된 ID를 사용합니다.")
    public ResponseEntity<PortfolioCreateResponse> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PortfolioRequest request) {
        PortfolioCreateResponse response = portfolioService.create(user, request);
        return ResponseEntity.created(URI.create("/portfolios/" + response.id())).body(response);
    }
    @PutMapping("/{portfolioId}")
    @SecurityRequirement(name = "cookieAuth")
    @Operation(summary = "포트폴리오 수정", description = "작성자만 제목·서식·블록 전체를 수정할 수 있습니다.")
    public ResponseEntity<Void> update(@PathVariable Long portfolioId,
            @AuthenticationPrincipal User user, @Valid @RequestBody PortfolioRequest request) {
        portfolioService.update(portfolioId, user, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{portfolioId}")
    @SecurityRequirement(name = "cookieAuth")
    @Operation(summary = "포트폴리오 삭제", description = "작성자만 삭제할 수 있습니다.")
    public ResponseEntity<Void> delete(@PathVariable Long portfolioId, @AuthenticationPrincipal User user) {
        portfolioService.delete(portfolioId, user);
        return ResponseEntity.noContent().build();
    }

}
