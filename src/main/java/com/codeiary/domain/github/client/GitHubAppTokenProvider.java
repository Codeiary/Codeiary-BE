package com.codeiary.domain.github.client;

import com.codeiary.domain.github.exception.GitHubErrorCode;
import com.codeiary.global.exception.RestApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.jsonwebtoken.Jwts;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GitHubAppTokenProvider {
    private final RestClient client;
    private final String clientId;
    private final String keyPath;

    public GitHubAppTokenProvider(@Qualifier("githubRestClient") RestClient client,
            @Value("${spring.security.oauth2.client.registration.github.client-id}") String clientId,
            @Value("${github.app-private-key-path:}") String keyPath) {
        this.client = client;
        this.clientId = clientId;
        this.keyPath = keyPath;
    }

    public String token(long installationId, long repositoryId) {
        if (keyPath.isBlank()) throw new IllegalStateException("GitHub App private key is not configured");
        RSAPrivateKey key;
        try (InputStream input = Files.newInputStream(Path.of(keyPath))) {
            key = RsaKeyConverters.pkcs8().convert(input);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read GitHub App PKCS8 private key");
        }
        Instant now = Instant.now();
        String jwt = Jwts.builder().issuer(clientId).issuedAt(Date.from(now.minusSeconds(60)))
                .expiration(Date.from(now.plusSeconds(540))).signWith(key, Jwts.SIG.RS256).compact();
        var response = client.post().uri("/app/installations/{id}/access_tokens", installationId)
                .headers(headers -> headers.setBearerAuth(jwt))
                .body(Map.of("repository_ids", List.of(repositoryId), "permissions", Map.of("contents", "write")))
                .retrieve().body(InstallationToken.class);
        if (response == null || response.token() == null) {
            throw new RestApiException(GitHubErrorCode.GITHUB_UNAVAILABLE);
        }
        return response.token();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstallationToken(String token) {}
}
