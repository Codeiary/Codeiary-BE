package com.codeiary.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource") // Spring Boot owns the container's start/stop lifecycle.
    PostgreSQLContainer postgresContainer() {
        var container = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
        container.withDatabaseName("codeiary_test");
        return container;
    }
}
