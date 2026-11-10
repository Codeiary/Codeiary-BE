package com.codeiary.domain.github.client;

import com.codeiary.domain.github.entity.GitHubConnection;
import com.codeiary.global.logging.LogExecution;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@Profile("loadtest")
@LogExecution
public class MockGitHubBlogClient implements GitHubBlogWriter {
    private final Duration delay;

    public MockGitHubBlogClient(@Value("${github.mock-delay:500ms}") Duration delay, Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("GitHub mock cannot run with the prod profile");
        }
        if (delay.isNegative()) throw new IllegalArgumentException("GitHub mock delay must not be negative");
        this.delay = delay;
    }

    @Override
    public void sync(GitHubConnection connection, Long postId, String markdown) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub mock interrupted");
        }
    }
}
