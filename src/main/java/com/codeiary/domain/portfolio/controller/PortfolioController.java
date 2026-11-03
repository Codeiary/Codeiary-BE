package com.codeiary.domain.portfolio.controller;

import com.codeiary.domain.portfolio.dto.request.PortfolioCreateRequest;
import com.codeiary.domain.portfolio.dto.response.PortfolioCreateResponse;
import com.codeiary.domain.portfolio.dto.response.PortfolioFontResponse;
import java.util.List;
import com.codeiary.domain.portfolio.service.PortfolioService;
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
            @Valid @RequestBody PortfolioCreateRequest request) {
        PortfolioCreateResponse response = portfolioService.create(user, request);
        return ResponseEntity.created(URI.create("/portfolios/" + response.id())).body(response);
    }
}
