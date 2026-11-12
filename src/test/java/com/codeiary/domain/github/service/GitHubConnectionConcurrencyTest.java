package com.codeiary.domain.github.service;

import com.codeiary.domain.auth.dto.AuthInfo;
import com.codeiary.domain.github.dto.GitHubLinkContext;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.entity.enums.OAuthProvider;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.support.IntegrationTestSupport;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubConnectionConcurrencyTest extends IntegrationTestSupport {
    @Autowired UserRepository users;
    @Autowired GitHubConnectionRepository connections;
    @Autowired GitHubConnectionService service;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void disconnectWaitsForPendingConnectionAndRemovesIt() throws Exception {
        var transaction = new TransactionTemplate(transactionManager);
        Long userId = transaction.execute(status -> users.saveAndFlush(
                User.create(UUID.randomUUID() + "@example.com", "기록자", Role.USER)).getId());
        var linked = new CompletableFuture<Void>();
        var commit = new CompletableFuture<Void>();
        var disconnectStarted = new CompletableFuture<Void>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var connecting = executor.submit(() -> transaction.executeWithoutResult(status -> {
                    service.connect(new AuthInfo(OAuthProvider.GITHUB, userId.toString(), null, "octocat", false, false),
                            new GitHubLinkContext(userId, Instant.now()));
                    linked.complete(null);
                    commit.join();
                }));
                linked.get(5, TimeUnit.SECONDS);
                var disconnecting = executor.submit(() -> {
                    disconnectStarted.complete(null);
                    service.disconnect(userId);
                });
                disconnectStarted.get(5, TimeUnit.SECONDS);
                assertThatThrownBy(() -> disconnecting.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                commit.complete(null);
                connecting.get(5, TimeUnit.SECONDS);
                disconnecting.get(5, TimeUnit.SECONDS);
                assertThat(connections.findByUserId(userId)).isEmpty();
            } finally {
                commit.complete(null);
            }
        } finally {
            users.deleteById(userId);
        }
    }
}
