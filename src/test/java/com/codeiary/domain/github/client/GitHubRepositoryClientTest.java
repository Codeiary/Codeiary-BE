package com.codeiary.domain.github.client;

import com.codeiary.domain.github.exception.GitHubErrorCode;
import com.codeiary.global.exception.RestApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitHubRepositoryClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GitHubRepositoryClient client = new GitHubRepositoryClient(builder.build());

    @Test
    void mapsGithubFieldsAndAuthenticatesOnlyAgainstFixedGithubEndpoint() {
        server.expect(requestTo("https://api.github.com/user/installations/10/repositories?per_page=100&page=2"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        {"total_count":31,"repositories":[{"id":20,"name":"blog","owner":{"id":123,"login":"octocat"},
                        "private":true,"default_branch":"main","archived":false,"disabled":false,"permissions":{"push":true}}]}
                        """, MediaType.APPLICATION_JSON));
        var result = client.repositories("test-token", 10, 2);
        assertThat(result.totalCount()).isEqualTo(31);
        assertThat(result.repositories().getFirst().defaultBranch()).isEqualTo("main");
        assertThat(result.repositories().getFirst().writable()).isTrue();
        server.verify();
    }

    @Test
    void missingRepositoryIsCreatedOnceAsPrivateWithReadme() {
        server.expect(requestTo("https://api.github.com/repos/octocat/CodeiaryBlog"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"message\":\"Not Found\"}").contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/user/repos"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().json("{\"name\":\"CodeiaryBlog\",\"private\":true,\"auto_init\":true,\"description\":\"Codeiary blog posts\"}"))
                .andRespond(withStatus(HttpStatus.CREATED).body(repositoryJson()).contentType(MediaType.APPLICATION_JSON));
        assertThat(client.ensureBlogRepository("test-token", "octocat").name()).isEqualTo("CodeiaryBlog");
        server.verify();
    }

    @Test
    void existingRepositoryIsReusedWithoutPostingOrChangingVisibility() {
        server.expect(requestTo("https://api.github.com/repos/octocat/CodeiaryBlog"))
                .andRespond(withSuccess(repositoryJson(), MediaType.APPLICATION_JSON));
        assertThat(client.ensureBlogRepository("test-token", "octocat").id()).isEqualTo(20);
        server.verify();
    }

    @Test
    void concurrentCreationConflictRefetchesInsteadOfCreatingAnotherRepository() {
        server.expect(requestTo("https://api.github.com/repos/octocat/CodeiaryBlog"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.github.com/user/repos"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT));
        server.expect(requestTo("https://api.github.com/repos/octocat/CodeiaryBlog"))
                .andRespond(withSuccess(repositoryJson(), MediaType.APPLICATION_JSON));
        assertThat(client.ensureBlogRepository("test-token", "octocat").id()).isEqualTo(20);
        server.verify();
    }

    @Test
    void invisibleExistingRepositoryIsNotOverwrittenWhenCreationFails() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.github.com/user/repos"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT));
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.ensureBlogRepository("test-token", "octocat"))
                .isInstanceOfSatisfying(RestApiException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(GitHubErrorCode.GITHUB_REPOSITORY_CREATION_FAILED));
        server.verify();
    }

    private String repositoryJson() {
        return """
                {"id":20,"name":"CodeiaryBlog","owner":{"id":123,"login":"octocat"},
                "private":true,"default_branch":"main","archived":false,"disabled":false,"permissions":{"push":true},
                "description":"Codeiary blog posts"}
                """;
    }

    @Test
    void creationPermissionFailureExplainsAppInstallationInsteadOfMisreportingOauthFailure() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.github.com/user/repos")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.ensureBlogRepository("test-token", "octocat"))
                .isInstanceOfSatisfying(RestApiException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(GitHubErrorCode.GITHUB_APP_INSTALLATION_REQUIRED));
        server.verify();
    }

    @Test
    void expiredGithubTokenDoesNotBecomeLocalLoginFailureOrLeakUpstreamBody() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .body("upstream-private-data"));
        assertThatThrownBy(() -> client.installations("test-token", 1))
                .isInstanceOfSatisfying(RestApiException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(GitHubErrorCode.GITHUB_REAUTH_REQUIRED);
                    assertThat(error.getCause()).isNull();
                    assertThat(error.getMessage()).doesNotContain("upstream-private-data");
                });
        server.verify();
    }
}
