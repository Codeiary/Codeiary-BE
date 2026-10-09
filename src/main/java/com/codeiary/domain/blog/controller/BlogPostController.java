package com.codeiary.domain.blog.controller;

import com.codeiary.domain.blog.dto.request.BlogPostRequest;
import com.codeiary.domain.blog.dto.request.BlogPostSort;
import com.codeiary.domain.blog.dto.response.BlogPostPageResponse;
import com.codeiary.domain.blog.dto.response.BlogPostResponse;
import com.codeiary.domain.blog.service.BlogPostService;
import com.codeiary.domain.blog.service.BlogPostListService;
import com.codeiary.domain.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Tag(name = "게시글")
public class BlogPostController {

    private final BlogPostService postService;
    private final BlogPostListService postListService;

    @GetMapping
    @Operation(summary = "공개 게시글 목록 조회", description = "검색·카테고리 필터와 최신순·조회순 정렬을 지원합니다.")
    public BlogPostPageResponse list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "LATEST") BlogPostSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return postListService.getPublicPosts(search, category, sort, page, size);
    }

    @GetMapping("/mine")
    @SecurityRequirement(name = "cookieAuth")
    @Operation(summary = "내 게시글 목록 조회", description = "공개·비공개 글을 최신순으로 조회합니다.")
    public BlogPostPageResponse mine(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return postListService.getMyPosts(user, search, category, page, size);
    }

    @PostMapping
    @SecurityRequirement(name = "cookieAuth")
    @Operation(summary = "게시글 생성")
    public ResponseEntity<BlogPostResponse> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody BlogPostRequest request
    ) {
        BlogPostResponse response = postService.create(user, request);
        return ResponseEntity.created(URI.create("/api/posts/" + response.id())).body(response);
    }

    @GetMapping("/{postId}")
    @Operation(summary = "게시글 단건 조회", description = "공개 글은 누구나, 비공개 글은 작성자만 조회할 수 있습니다.")
    public BlogPostResponse get(@PathVariable Long postId, @AuthenticationPrincipal User user) {
        return postService.get(postId, user);
    }

    @PutMapping("/{postId}")
    @SecurityRequirement(name = "cookieAuth")
    @Operation(summary = "게시글 수정", description = "작성자만 수정할 수 있습니다. 선택 항목을 비우면 삭제합니다.")
    public BlogPostResponse update(
            @PathVariable Long postId,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody BlogPostRequest request
    ) {
        return postService.update(postId, user, request);
    }

    @DeleteMapping("/{postId}")
    @SecurityRequirement(name = "cookieAuth")
    @Operation(summary = "게시글 삭제", description = "작성자만 삭제할 수 있습니다.")
    public ResponseEntity<Void> delete(@PathVariable Long postId, @AuthenticationPrincipal User user) {
        postService.delete(postId, user);
        return ResponseEntity.noContent().build();
    }
}
