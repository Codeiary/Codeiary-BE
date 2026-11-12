package com.codeiary.domain.github.repository;

import com.codeiary.domain.github.dto.outbox.ClaimedWork;
import com.codeiary.domain.github.dto.outbox.ProcessingWork;
import java.util.List;
import java.util.Optional;

public interface GitHubBlogOutboxRepositoryCustom {
    List<ClaimedWork> claimDue(int limit, int maxFailures);

    void markEnqueued(ClaimedWork work);

    void retryNextCycle(ClaimedWork work, int maxFailures);

    Optional<ProcessingWork> claimForProcessing(long id);

    boolean markCompleted(ProcessingWork work);

    boolean retryProcessing(ProcessingWork work, int maxFailures);

    boolean isFinishedOrMissing(long id);

}
