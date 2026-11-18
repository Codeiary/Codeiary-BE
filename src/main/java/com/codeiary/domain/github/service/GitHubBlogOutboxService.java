package com.codeiary.domain.github.service;

import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.exception.BlogErrorCode;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.github.config.GitHubBlogSyncConfig;
import com.codeiary.domain.github.dto.outbox.ClaimedWork;
import com.codeiary.domain.github.dto.outbox.GitHubBlogSyncContent;
import com.codeiary.domain.github.dto.outbox.ProcessingWork;
import com.codeiary.domain.github.dto.response.GitHubPostSyncResponse;
import com.codeiary.domain.github.entity.GitHubBlogOutbox;
import com.codeiary.domain.github.entity.enums.EventType;
import com.codeiary.domain.github.repository.GitHubBlogOutboxRepository;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.logging.LogExecution;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GitHubBlogOutboxService {
  private final GitHubConnectionRepository gitHubConnectionRepository;
  private final GitHubBlogOutboxRepository gitHubBlogOutboxRepository;
  private final BlogPostRepository blogPostRepository;
  private final GitHubBlogSyncConfig gitHubBlogSyncConfig;

  @Transactional(readOnly = true)
  public GitHubPostSyncResponse statusFor(Long postId, Long userId) {
    if (!blogPostRepository.existsByIdAndAuthorId(postId, userId)) {
      throw new RestApiException(BlogErrorCode.POST_NOT_FOUND);
    }
    boolean connected = gitHubConnectionRepository.existsByUserIdAndInstallationIdIsNotNull(userId);
    return new GitHubPostSyncResponse(
        connected,
        connected
            ? gitHubBlogOutboxRepository
                .findFirstByPostIdAndAuthorIdOrderByIdDesc(postId, userId)
                .map(GitHubBlogOutbox::getStatus)
                .orElse(null)
            : null);
  }

  public void savePendingChange(BlogPost blogPost, EventType eventType, String previousPath) {
    Long authorId = blogPost.getAuthor().getId();
    if (gitHubConnectionRepository.existsByUserIdAndInstallationIdIsNotNull(authorId)) {
      String targetPath =
          eventType != EventType.DELETED && blogPost.isPublicPost()
              ? GitHubBlogPath.of(blogPost)
              : null;
      gitHubBlogOutboxRepository.save(
          GitHubBlogOutbox.create(
              blogPost.getId(),
              authorId,
              eventType,
              blogPost.getTitle(),
              previousPath,
              targetPath));
    }
  }

  @Transactional
  public List<ClaimedWork> claimDue(int limit) {
    return gitHubBlogOutboxRepository.claimDue(limit, gitHubBlogSyncConfig.getMaxFailures());
  }

  @Transactional
  public void markEnqueued(ClaimedWork claimedWork) {
    gitHubBlogOutboxRepository.markEnqueued(claimedWork);
  }

  @Transactional
  public void retryNextCycle(ClaimedWork claimedWork) {
    gitHubBlogOutboxRepository.retryNextCycle(claimedWork, gitHubBlogSyncConfig.getMaxFailures());
  }

  @Transactional
  public Optional<ProcessingWork> claimForProcessing(long id) {
    return gitHubBlogOutboxRepository.claimForProcessing(id);
  }

  @Transactional
  public boolean markCompleted(ProcessingWork processingWork) {
    return gitHubBlogOutboxRepository.markCompleted(processingWork);
  }

  @Transactional
  public boolean retryProcessing(ProcessingWork processingWork) {
    return gitHubBlogOutboxRepository.retryProcessing(
        processingWork, gitHubBlogSyncConfig.getMaxFailures());
  }

  @Transactional(readOnly = true)
  public boolean isFinishedOrMissing(long id) {
    return gitHubBlogOutboxRepository.isFinishedOrMissing(id);
  }

  @Transactional(readOnly = true)
  @LogExecution
  public GitHubBlogSyncContent read(ProcessingWork processingWork) {
    var connection =
        gitHubConnectionRepository.findByUserId(processingWork.authorId()).orElse(null);
    if (connection == null || connection.getInstallationId() == null) {
      return null;
    }
    var change = gitHubBlogOutboxRepository.findById(processingWork.id()).orElseThrow();
    var post =
        blogPostRepository
            .findById(processingWork.postId())
            .filter(candidate -> candidate.getAuthor().getId().equals(processingWork.authorId()))
            .orElse(null);
    String markdown = markdownFor(post, processingWork.authorId());
    String path = markdown == null ? null : GitHubBlogPath.of(post);
    var obsoletePaths = new LinkedHashSet<String>();
    if (change.getPreviousPath() != null) obsoletePaths.add(change.getPreviousPath());
    if (change.getTargetPath() != null) obsoletePaths.add(change.getTargetPath());
    if (change.getEventType() != EventType.CREATED)
      obsoletePaths.add(GitHubBlogPath.legacy(processingWork.postId()));
    obsoletePaths.remove(path);
    String title = post == null ? change.getTitle() : post.getTitle();
    if (title == null) title = String.valueOf(processingWork.postId());
    return new GitHubBlogSyncContent(
        connection, title, change.getEventType(), markdown, path, List.copyOf(obsoletePaths));
  }

  private String markdownFor(BlogPost blogPost, Long authorId) {
    if (blogPost == null
        || !blogPost.isPublicPost()
        || !blogPost.getAuthor().getId().equals(authorId)) {
      return null;
    }
    return "# " + blogPost.getTitle() + "\n\n" + blogPost.getContent() + "\n";
  }
}
