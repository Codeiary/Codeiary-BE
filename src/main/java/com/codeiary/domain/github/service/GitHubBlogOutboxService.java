package com.codeiary.domain.github.service;

import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.exception.BlogErrorCode;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.github.dto.outbox.ClaimedWork;
import com.codeiary.domain.github.dto.outbox.GitHubBlogSyncContent;
import com.codeiary.domain.github.dto.outbox.ProcessingWork;
import com.codeiary.domain.github.config.GitHubBlogSyncConfig;
import com.codeiary.domain.github.dto.response.GitHubPostSyncResponse;
import com.codeiary.domain.github.entity.GitHubBlogOutbox;
import com.codeiary.domain.github.entity.enums.EventType;
import com.codeiary.domain.github.repository.GitHubBlogOutboxRepository;
import com.codeiary.global.logging.LogExecution;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GitHubBlogOutboxService {
    private final GitHubConnectionRepository connections;
    private final GitHubBlogOutboxRepository outbox;
    private final BlogPostRepository posts;
    private final GitHubBlogSyncConfig config;

    @Transactional(readOnly = true)
    public GitHubPostSyncResponse statusFor(Long postId, Long userId) {
        if (!posts.existsByIdAndAuthorId(postId, userId)) {
            throw new RestApiException(BlogErrorCode.POST_NOT_FOUND);
        }
        boolean connected = connections.existsByUserIdAndInstallationIdIsNotNull(userId);
        return new GitHubPostSyncResponse(connected, connected
                ? outbox.findFirstByPostIdAndAuthorIdOrderByIdDesc(postId, userId)
                        .map(GitHubBlogOutbox::getStatus).orElse(null)
                : null);
    }

    public void savePendingChange(BlogPost post, EventType eventType, String previousPath) {
        Long authorId = post.getAuthor().getId();
        if (connections.existsByUserIdAndInstallationIdIsNotNull(authorId)) {
            String targetPath = eventType != EventType.DELETED && post.isPublicPost()
                    ? GitHubBlogPath.of(post) : null;
            outbox.save(GitHubBlogOutbox.create(post.getId(), authorId, eventType,
                    post.getTitle(), previousPath, targetPath));
        }
    }

    @Transactional
    public List<ClaimedWork> claimDue(int limit) {
        return outbox.claimDue(limit, config.getMaxFailures());
    }

    @Transactional
    public void markEnqueued(ClaimedWork work) {
        outbox.markEnqueued(work);
    }

    @Transactional
    public void retryNextCycle(ClaimedWork work) {
        outbox.retryNextCycle(work, config.getMaxFailures());
    }

    @Transactional
    public Optional<ProcessingWork> claimForProcessing(long id) {
        return outbox.claimForProcessing(id);
    }

    @Transactional
    public boolean markCompleted(ProcessingWork work) {
        return outbox.markCompleted(work);
    }

    @Transactional
    public boolean retryProcessing(ProcessingWork work) {
        return outbox.retryProcessing(work, config.getMaxFailures());
    }

    @Transactional(readOnly = true)
    public boolean isFinishedOrMissing(long id) {
        return outbox.isFinishedOrMissing(id);
    }


    @Transactional(readOnly = true)
    @LogExecution
    public GitHubBlogSyncContent read(ProcessingWork work) {
        var connection = connections.findByUserId(work.authorId()).orElse(null);
        if (connection == null || connection.getInstallationId() == null) {
            return null;
        }
        var change = outbox.findById(work.id()).orElseThrow();
        var post = posts.findById(work.postId())
                .filter(candidate -> candidate.getAuthor().getId().equals(work.authorId())).orElse(null);
        String markdown = markdownFor(post, work.authorId());
        String path = markdown == null ? null : GitHubBlogPath.of(post);
        var obsoletePaths = new LinkedHashSet<String>();
        if (change.getPreviousPath() != null) obsoletePaths.add(change.getPreviousPath());
        if (change.getTargetPath() != null) obsoletePaths.add(change.getTargetPath());
        if (change.getEventType() != EventType.CREATED) obsoletePaths.add(GitHubBlogPath.legacy(work.postId()));
        obsoletePaths.remove(path);
        String title = post == null ? change.getTitle() : post.getTitle();
        if (title == null) title = String.valueOf(work.postId());
        return new GitHubBlogSyncContent(connection, title, change.getEventType(), markdown,
                path, List.copyOf(obsoletePaths));
    }

    private String markdownFor(BlogPost post, Long authorId) {
        if (post == null || !post.isPublicPost() || !post.getAuthor().getId().equals(authorId)) {
            return null;
        }
        return "# " + post.getTitle() + "\n\n" + post.getContent() + "\n";
    }
}
