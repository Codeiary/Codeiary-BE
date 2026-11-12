package com.codeiary.domain.github.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component("githubBlogSyncConfig")
@ConfigurationProperties(prefix = "github.blog-sync")
public class GitHubBlogSyncConfig {
    private int publishIntervalMs;
    private int publishBatchSize;
    private int consumeIntervalMs;
    private int consumeBatchSize;
    private int maxFailures;
    private Duration reclaimAfter;
    private String consumerGroup;
    private String streamKey;
}
