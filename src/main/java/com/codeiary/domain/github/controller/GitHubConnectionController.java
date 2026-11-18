package com.codeiary.domain.github.controller;

import com.codeiary.domain.github.dto.response.GitHubConnectionResponse;
import com.codeiary.domain.github.service.GitHubConnectionService;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.logging.LogExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@LogExecution
@RequestMapping("/github/connection")
@RequiredArgsConstructor
public class GitHubConnectionController {
  private final GitHubConnectionService gitHubConnectionService;

  @GetMapping
  public ResponseEntity<GitHubConnectionResponse> connection(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(gitHubConnectionService.findConnection(user.getId()));
  }

  @DeleteMapping
  public ResponseEntity<Void> disconnect(@AuthenticationPrincipal User user) {
    gitHubConnectionService.disconnect(user.getId());
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
