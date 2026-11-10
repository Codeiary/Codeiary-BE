package com.codeiary.domain.github.client;

import io.jsonwebtoken.Jwts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitHubAppTokenProviderTest {
    @TempDir Path directory;

    @Test
    void signsShortLivedAppJwtAndScopesInstallationTokenToOneRepository() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var path = directory.resolve("app.pem");
        Files.writeString(path, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        var builder = RestClient.builder().baseUrl("https://api.github.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var provider = new GitHubAppTokenProvider(builder.build(), "client-id", path.toString());
        server.expect(requestTo("https://api.github.com/app/installations/10/access_tokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    var jwt = request.getHeaders().getFirst("Authorization").substring("Bearer ".length());
                    var claims = Jwts.parser().verifyWith(pair.getPublic()).build().parseSignedClaims(jwt).getPayload();
                    assertThat(claims.getIssuer()).isEqualTo("client-id");
                    assertThat(claims.getExpiration().toInstant()).isBefore(Instant.now().plusSeconds(600));
                })
                .andExpect(jsonPath("$.repository_ids[0]").value(20))
                .andExpect(jsonPath("$.permissions.contents").value("write"))
                .andRespond(withSuccess("{\"token\":\"installation-token\"}", MediaType.APPLICATION_JSON));
        assertThat(provider.token(10, 20)).isEqualTo("installation-token");
        server.verify();
    }
}
