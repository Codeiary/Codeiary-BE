package com.codeiary.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("media.s3")
public record MediaStorageProperties(String bucket, String publicBaseUrl, String region) {
}
