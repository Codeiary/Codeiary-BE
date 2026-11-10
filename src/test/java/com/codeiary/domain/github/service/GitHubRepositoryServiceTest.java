package com.codeiary.domain.github.service;

import com.codeiary.domain.github.client.GitHubRepositoryClient;
import com.codeiary.domain.github.client.GitHubRepositoryClient.*;
import com.codeiary.domain.github.entity.GitHubConnection;
import com.codeiary.domain.github.exception.GitHubErrorCode;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.global.exception.RestApiException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubRepositoryServiceTest {
    @Mock GitHubConnectionRepository connections;
    @Mock GitHubConnectionService connectionService;
    @Mock OAuth2AuthorizedClientService authorizedClients;
    @Mock GitHubRepositoryClient client;
    @InjectMocks GitHubRepositoryService service;
    private final Account owner = new Account(123, "octocat");

    @Test
    void installationFailurePreservesDomainErrorAndDoesNotSaveRepository() {
        token();
        var failure = new RestApiException(GitHubErrorCode.GITHUB_ACCESS_DENIED);
        given(client.ensureBlogRepository("test-token", "octocat")).willReturn(repository(20, true, false, false));
        given(client.installations("test-token", 1)).willThrow(failure);
        assertThatThrownBy(() -> service.prepare(1L)).isSameAs(failure);
        verifyNoInteractions(connectionService);
        verify(client, never()).repositories(anyString(), anyLong(), anyInt());
    }

    @Test
    void missingMembershipStillPreventsCompletedInstallationLink() {
        token();
        var repository = repository(20, true, false, false);
        given(client.ensureBlogRepository("test-token", "octocat")).willReturn(repository);
        given(client.installations("test-token", 1)).willReturn(new Installations(1,
                List.of(new Installation(10, owner, Map.of("contents", "write"), null))));
        given(client.repositories("test-token", 10, 1)).willReturn(new Repositories(0, List.of()));
        service.prepare(1L);
        verify(connectionService).saveRepository(1L, 5L, 123L, null, repository);
    }

    @Test
    void missingTokenRequiresGithubReauthenticationWithoutCallingGithub() {
        connection();
        assertError(() -> service.prepare(1L), GitHubErrorCode.GITHUB_REAUTH_REQUIRED);
        verifyNoInteractions(client, connectionService);
    }

    @Test
    void expiredTokenRequiresReauthentication() {
        token(Instant.now().minusSeconds(1));
        assertError(() -> service.prepare(1L), GitHubErrorCode.GITHUB_REAUTH_REQUIRED);
        verifyNoInteractions(client);
    }

    @Test
    void createsFixedRepositoryAndSavesGithubValuesWithoutRequiringUserSelection() {
        token();
        var repository = repository(20, true, false, false);
        given(client.ensureBlogRepository("test-token", "octocat")).willReturn(repository);
        given(client.installations("test-token", 1)).willReturn(new Installations(1,
                List.of(new Installation(10, owner, Map.of("contents", "write"), null))));
        given(client.repositories("test-token", 10, 1)).willReturn(new Repositories(1, List.of(repository)));
        service.prepare(1L);
        verify(connectionService).saveRepository(1L, 5L, 123L, 10L, repository);
    }

    @Test
    void createdRepositoryWithoutInstallationIsSavedAsPendingPermission() {
        token();
        var repository = repository(20, true, false, false);
        given(client.ensureBlogRepository("test-token", "octocat")).willReturn(repository);
        given(client.installations("test-token", 1)).willReturn(new Installations(0, List.of()));
        service.prepare(1L);
        verify(connectionService).saveRepository(1L, 5L, 123L, null, repository);
    }

    @Test
    void neverLinksRepositoryOwnedBySomeoneElse() {
        token();
        given(client.ensureBlogRepository("test-token", "octocat")).willReturn(new Repository(
                20, "CodeiaryBlog", new Account(999, "other"), "main", false, false, Map.of("push", true)));
        assertError(() -> service.prepare(1L), GitHubErrorCode.GITHUB_REPOSITORY_UNAVAILABLE);
        verifyNoInteractions(connectionService);
    }

    @Test
    void ignoresForeignOrSuspendedInstallationsAndNeverGrantsInstallationFromAnotherAccount() {
        token();
        var repository = repository(20, true, false, false);
        given(client.ensureBlogRepository("test-token", "octocat")).willReturn(repository);
        given(client.installations("test-token", 1)).willReturn(new Installations(101, List.of(
                new Installation(10, new Account(999, "other"), Map.of("contents", "write"), null),
                new Installation(11, owner, Map.of("contents", "write"), "2026-01-01T00:00:00Z"))));
        given(client.installations("test-token", 2)).willReturn(new Installations(101,
                List.of(new Installation(12, owner, Map.of("contents", "write"), null))));
        given(client.repositories("test-token", 12, 1)).willReturn(new Repositories(101, List.of()));
        given(client.repositories("test-token", 12, 2)).willReturn(new Repositories(101, List.of(repository)));
        service.prepare(1L);
        verify(connectionService).saveRepository(1L, 5L, 123L, 12L, repository);
        verify(client, never()).repositories(anyString(), eq(10L), anyInt());
    }

    private void connection() {
        var connection = GitHubConnection.create(UserFixture.createWithId(), 123L);
        connection.connect("octocat");
        ReflectionTestUtils.setField(connection, "id", 5L);
        given(connections.findByUserId(1L)).willReturn(Optional.of(connection));
    }

    private void token() { token(Instant.now().plusSeconds(3600)); }
    private void token(Instant expiresAt) {
        connection();
        var registration = ClientRegistration.withRegistrationId("github").clientId("test-client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://localhost/callback")
                .authorizationUri("https://github.com/login/oauth/authorize").tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user").userNameAttributeName("id").build();
        given(authorizedClients.loadAuthorizedClient("github", "123")).willReturn(new OAuth2AuthorizedClient(
                registration, "123", new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                        "test-token", Instant.now().minusSeconds(3600), expiresAt)));
    }

    private Repository repository(long id, boolean push, boolean archived, boolean disabled) {
        return new Repository(id, "CodeiaryBlog", owner, "main", archived, disabled, Map.of("push", push));
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, GitHubErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(RestApiException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }
}
