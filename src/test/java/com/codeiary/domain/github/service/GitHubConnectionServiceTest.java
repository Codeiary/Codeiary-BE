package com.codeiary.domain.github.service;

import com.codeiary.domain.auth.dto.AuthInfo;
import com.codeiary.domain.github.dto.GitHubLinkContext;
import com.codeiary.domain.github.entity.GitHubConnection;
import com.codeiary.domain.github.exception.GitHubErrorCode;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import com.codeiary.domain.user.entity.enums.OAuthProvider;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.global.exception.RestApiException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GitHubConnectionServiceTest {
    @Mock UserRepository users;
    @Mock GitHubConnectionRepository connections;
    @Mock OAuth2AuthorizedClientService authorizedClients;
    @InjectMocks GitHubConnectionService service;

    @ParameterizedTest
    @ValueSource(strings = {"github_connection_user_id_key", "github_connection_github_user_id_key"})
    void translateAccountUniqueViolations(String constraint) {
        var failure = failOnSave(constraint);
        assertThat(connect()).isInstanceOfSatisfying(RestApiException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(GitHubErrorCode.GITHUB_ACCOUNT_CONFLICT);
            assertThat(exception.getCause()).isSameAs(failure);
        });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "github_connection_user_id_fkey")
    void preserveOtherIntegrityViolations(String constraint) {
        var failure = failOnSave(constraint);
        assertThat(connect()).isSameAs(failure);
    }

    private DataIntegrityViolationException failOnSave(String constraint) {
        given(users.findByIdForUpdate(UserFixture.ID)).willReturn(Optional.of(UserFixture.createWithId()));
        var failure = new DataIntegrityViolationException("integrity violation",
                new ConstraintViolationException("constraint", new SQLException(), constraint));
        given(connections.saveAndFlush(any(GitHubConnection.class))).willThrow(failure);
        return failure;
    }

    private Throwable connect() {
        return catchThrowable(() -> service.connect(
                new AuthInfo(OAuthProvider.GITHUB, "123", null, "octocat", false, false),
                new GitHubLinkContext(UserFixture.ID, Instant.now())));
    }
}
