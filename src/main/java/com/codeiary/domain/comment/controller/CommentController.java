package com.codeiary.domain.comment.controller;

import com.codeiary.domain.comment.dto.request.CommentCreateRequest;
import com.codeiary.domain.comment.dto.request.CommentUpdateRequest;
import com.codeiary.domain.comment.dto.response.CommentResponse;
import com.codeiary.domain.comment.service.CommentService;
import com.codeiary.domain.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
@Tag(name = "댓글")
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    @Operation(summary = "게시글 댓글 조회")
    public List<CommentResponse> list(
            @PathVariable Long postId,
            @AuthenticationPrincipal User viewer
    ) {
        return commentService.getComments(postId, viewer);
    }

    @PostMapping
    @SecurityRequirement(name = "cookieAuth")
    @Operation(summary = "댓글 작성")
    public ResponseEntity<CommentResponse> create(
            @PathVariable Long postId,
            @AuthenticationPrincipal User author,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        CommentResponse response = commentService.create(postId, author, request);
        return ResponseEntity.created(URI.create("/api/posts/" + postId + "/comments/" + response.id()))
                .body(response);
    }

    @PutMapping("/{commentId}")
    @SecurityRequirement(name = "cookieAuth")
    @Operation(summary = "댓글 수정")
    public CommentResponse update(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal User author,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        return commentService.update(postId, commentId, author, request);
    }

    @DeleteMapping("/{commentId}")
    @SecurityRequirement(name = "cookieAuth")
    @Operation(summary = "댓글 삭제")
    public ResponseEntity<Void> delete(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal User author
    ) {
        commentService.delete(postId, commentId, author);
        return ResponseEntity.noContent().build();
    }
}
