package com.codeiary.domain.github.dto.outbox;

public record ProcessingWork(long id, long postId, long authorId, int claimVersion) {
}
