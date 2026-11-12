package com.codeiary.domain.github.repository;

import com.codeiary.domain.github.dto.outbox.ClaimedWork;
import com.codeiary.domain.github.dto.outbox.ProcessingWork;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GitHubBlogOutboxRepositoryImpl implements GitHubBlogOutboxRepositoryCustom {
    private static final int CLAIM_TIMEOUT_SECONDS = 30;
    private static final int PROCESS_TIMEOUT_SECONDS = 120;
    private static final int ENQUEUED_TIMEOUT_SECONDS = 300;

    private final JdbcTemplate jdbc;

    @Override
    public List<ClaimedWork> claimDue(int limit, int maxFailures) {
        return jdbc.query("""
                WITH due AS (
                    SELECT id
                      FROM github_blog_outbox
                     WHERE status IN ('PENDING', 'ENQUEUED', 'PROCESSING')
                       AND next_attempt_at <= CURRENT_TIMESTAMP
                     ORDER BY next_attempt_at, id
                     LIMIT ? FOR UPDATE SKIP LOCKED
                ), claimed AS (
                    UPDATE github_blog_outbox
                       SET status = CASE
                               WHEN status IN ('ENQUEUED', 'PROCESSING') AND failure_count + 1 >= ?
                               THEN 'FAILED' ELSE 'PENDING' END,
                           failure_count = failure_count + CASE
                               WHEN status IN ('ENQUEUED', 'PROCESSING') THEN 1 ELSE 0 END,
                           claim_version = claim_version + 1,
                           next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                           updated_at = CURRENT_TIMESTAMP
                     WHERE id IN (SELECT id FROM due)
                    RETURNING id, claim_version, status
                )
                SELECT id, claim_version FROM claimed WHERE status = 'PENDING'
                """, (row, index) -> new ClaimedWork(row.getLong("id"), row.getInt("claim_version")),
                limit, maxFailures, CLAIM_TIMEOUT_SECONDS);
    }

    @Override
    public void markEnqueued(ClaimedWork work) {
        jdbc.update("""
                UPDATE github_blog_outbox
                   SET status = 'ENQUEUED',
                       next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND status = 'PENDING' AND claim_version = ?
                """, ENQUEUED_TIMEOUT_SECONDS, work.id(), work.claimVersion());
    }

    @Override
    public void retryNextCycle(ClaimedWork work, int maxFailures) {
        jdbc.update("""
                UPDATE github_blog_outbox
                   SET status = CASE WHEN failure_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END,
                       failure_count = failure_count + 1,
                       next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '1 second',
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND status = 'PENDING' AND claim_version = ?
                """, maxFailures, work.id(), work.claimVersion());
    }

    @Override
    public Optional<ProcessingWork> claimForProcessing(long id) {
        List<ProcessingWork> rows = jdbc.query("""
                UPDATE github_blog_outbox
                   SET status = 'PROCESSING',
                       claim_version = claim_version + 1,
                       next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                   AND (status IN ('PENDING', 'ENQUEUED')
                        OR (status = 'PROCESSING' AND next_attempt_at <= CURRENT_TIMESTAMP))
                RETURNING post_id, author_id, claim_version
                """, (row, index) -> new ProcessingWork(id, row.getLong("post_id"),
                        row.getLong("author_id"), row.getInt("claim_version")),
                PROCESS_TIMEOUT_SECONDS, id);
        return rows.stream().findFirst();
    }

    @Override
    public boolean markCompleted(ProcessingWork work) {
        return jdbc.update("""
                UPDATE github_blog_outbox
                   SET status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND status = 'PROCESSING' AND claim_version = ?
                """, work.id(), work.claimVersion()) == 1;
    }

    @Override
    public boolean retryProcessing(ProcessingWork work, int maxFailures) {
        return jdbc.update("""
                UPDATE github_blog_outbox
                   SET status = CASE WHEN failure_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END,
                       failure_count = failure_count + 1,
                       next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '5 seconds',
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND status = 'PROCESSING' AND claim_version = ?
                """, maxFailures, work.id(), work.claimVersion()) == 1;
    }

    @Override
    public boolean isFinishedOrMissing(long id) {
        List<String> statuses = jdbc.query("SELECT status FROM github_blog_outbox WHERE id = ?",
                (row, index) -> row.getString("status"), id);
        return statuses.isEmpty() || statuses.getFirst().equals("COMPLETED")
                || statuses.getFirst().equals("FAILED");
    }

}
