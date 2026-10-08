package com.codeiary.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(MediaStorageProperties.class)
public class MediaStorageConfig {

    @Bean
    public S3Presigner s3Presigner(MediaStorageProperties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .serviceConfiguration(S3Configuration.builder().checksumValidationEnabled(false).build())
                .build();
    }
}
