package com.codeiary.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.codeiary.domain.auth.service.TokenService;
import com.codeiary.domain.github.client.GitHubRepositoryClient;
import com.codeiary.domain.github.client.GitHubRepositoryClient.*;
import com.codeiary.domain.github.dto.GitHubLinkContext;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import com.codeiary.domain.user.entity.enums.OAuthProvider;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.global.security.dto.AuthInfo;
import com.codeiary.global.security.dto.oauth2user.CustomOAuth2User;
import com.codeiary.global.security.handler.CustomOauth2SuccessHandler;
import com.codeiary.global.security.repository.OAuth2RequestRepository;
import com.codeiary.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class GitHubRepositoryIntegrationTest extends IntegrationTestSupport {
  @Autowired MockMvc mvc;
  @Autowired CustomOauth2SuccessHandler successHandler;
  @Autowired UserRepository users;
  @Autowired GitHubConnectionRepository connections;
  @Autowired TokenService tokenService;
  @Autowired OAuth2AuthorizedClientService authorizedClients;
  @Autowired ClientRegistrationRepository registrations;
  @MockitoBean GitHubRepositoryClient client;

  @DisplayName("OAuth 콜백에서 추가 요청 없이 저장소를 생성하고 연결할 수 있다.")
  @Test
  void oauthCallbackCreatesAndConnectsRepositoryWithoutAnotherRequest() throws Exception {
    var user = users.saveAndFlush(UserFixture.create(Role.USER));
    authorizedClients.saveAuthorizedClient(
        new OAuth2AuthorizedClient(
            registrations.findByRegistrationId("github"),
            "123",
            new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-token",
                Instant.now(),
                Instant.now().plusSeconds(3600))),
        UsernamePasswordAuthenticationToken.authenticated("123", "", List.of()));
    var cookie = new Cookie("access_token", tokenService.login(user.getEmail()).accessToken());
    var owner = new Account(123, "octocat");
    given(client.ensureBlogRepository("test-token", "octocat"))
        .willReturn(
            new Repository(20, "CodeiaryBlog", owner, "main", false, false, Map.of("push", true)));
    given(client.installations("test-token", 1))
        .willReturn(
            new Installations(
                1, List.of(new Installation(10, owner, Map.of("contents", "write"), null))));
    given(client.repositories("test-token", 10, 1))
        .willReturn(
            new Repositories(
                1,
                List.of(
                    new Repository(
                        20, "CodeiaryBlog", owner, "main", false, false, Map.of("push", true)))));

    var callback = new MockHttpServletRequest();
    var authorization =
        OAuth2AuthorizationRequest.authorizationCode()
            .clientId("test-client")
            .authorizationUri("https://github.com/login/oauth/authorize")
            .redirectUri("http://localhost:8080/login/oauth2/code/github")
            .state("test-state")
            .attributes(values ->
                values.put(
                    GitHubLinkContext.ATTRIBUTE,
                    new GitHubLinkContext(user.getId(), Instant.now())))
            .build();
    callback.setAttribute(OAuth2RequestRepository.COMPLETED_REQUEST, authorization);
    var principal =
        new CustomOAuth2User(
            new DefaultOAuth2User(List.of(), Map.of("id", 123), "id"),
            new AuthInfo(OAuthProvider.GITHUB, "123", null, "octocat", false, false));
    var response = new MockHttpServletResponse();
    successHandler.onAuthenticationSuccess(
        callback,
        response,
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/auth/callback");
    assertThat(response.getCookies()).isEmpty();
    verify(client).ensureBlogRepository("test-token", "octocat");
    connections.flush();
    assertThat(connections.findByUserId(user.getId()).orElseThrow().getRepositoryId())
        .isEqualTo(20);
    mvc.perform(get("/github/connection").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("repository.name").value("CodeiaryBlog"));
    mvc.perform(delete("/github/connection").cookie(cookie)).andExpect(status().isNoContent());
    assertThat(connections.findByUserId(user.getId())).isEmpty();
    assertThat((Object) authorizedClients.loadAuthorizedClient("github", "123")).isNull();
  }
}
