package com.codeiary.global.security;

import com.codeiary.domain.github.dto.GitHubLinkContext;
import com.codeiary.domain.github.repository.GitHubConnectionRepository;
import com.codeiary.global.security.handler.CustomOauth2SuccessHandler;
import com.codeiary.global.security.repository.OAuth2RequestRepository;
import com.codeiary.global.security.dto.adapter.OAuth2ResponseFactory;
import com.codeiary.global.security.dto.oauth2user.CustomOAuth2User;
import com.codeiary.domain.auth.dto.AuthInfo;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.domain.github.service.GitHubConnectionService;
import com.codeiary.domain.user.entity.User;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.domain.auth.service.TokenSessionService;
import com.codeiary.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class GitHubConnectionStartTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TokenSessionService sessions;
    @Autowired OAuth2RequestRepository authorizationRequests;
    @Autowired CustomOauth2SuccessHandler successHandler;
    @Autowired GitHubConnectionRepository connections;
    @Autowired GitHubConnectionService githubConnections;

    @Test
    void rejectAnonymousAndPendingUsers() throws Exception {
        mvc.perform(get("/oauth2/authorization/github"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/oauth2/authorization/github").cookie(cookie(Role.PENDING)))
                .andExpect(status().isForbidden());
    }

    @Test
    void preserveUserInStateBoundOAuthRequest() throws Exception {
        var user = users.saveAndFlush(UserFixture.create(Role.USER));
        var token = sessions.createSession(user.getEmail()).accessToken();
        var result = mvc.perform(get("/oauth2/authorization/github")
                        .cookie(new Cookie("access_token", token))
                        .param("userId", "999999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
                        "Location", org.hamcrest.Matchers.startsWith("https://github.com/login/oauth/authorize?")))
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        var authorizationRepository = new HttpSessionOAuth2AuthorizationRequestRepository();
        // Load through the same repository used by Spring's callback filter.
        String state = java.util.Collections.list(session.getAttributeNames()).stream()
                .map(session::getAttribute)
                .filter(org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.class::isInstance)
                .map(org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.class::cast)
                .findFirst().orElseThrow().getState();
        var callback = get("/login/oauth2/code/github").session(session).param("state", state)
                .buildRequest(result.getRequest().getServletContext());
        var saved = authorizationRepository.loadAuthorizationRequest(callback);
        var context = saved.<GitHubLinkContext>getAttribute(GitHubLinkContext.ATTRIBUTE);
        assertThat(context.userId()).isEqualTo(user.getId());
        assertThat(saved.getAttribute("registration_id").toString()).isEqualTo("github");
        callback.setParameter("state", "different-state");
        assertThat(authorizationRepository.loadAuthorizationRequest(callback)).isNull();
    }

    @Test
    void preserveGoogleAuthorizationStart() throws Exception {
        mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void connectCallbackToOriginalUserWithoutIssuingLoginTokens() throws Exception {
        var user = users.saveAndFlush(UserFixture.create(Role.USER));
        long userCount = users.count();
        var authorization = authorization(new GitHubLinkContext(user.getId(), Instant.now()));
        var callback = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        authorizationRequests.saveAuthorizationRequest(authorization, callback, response);
        callback.setParameter("state", authorization.getState());
        assertThat(authorizationRequests.removeAuthorizationRequest(callback, response)).isNotNull();
        assertThat(authorizationRequests.removeAuthorizationRequest(callback, response)).isNull();
        successHandler.onAuthenticationSuccess(callback, response, githubAuthentication());
        var saved = connections.findByUserId(user.getId()).orElseThrow();
        assertThat(saved.getGithubUserId()).isEqualTo(123L);
        assertThat(saved.getGithubLogin()).isEqualTo("octocat");
        assertThat(response.getCookies()).isEmpty();
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/auth/callback");
        assertThat(users.count()).isEqualTo(userCount);
    }

    @Test
    void preserveGoogleLoginAfterMovingSuccessLogic() throws Exception {
        var attributes = Map.<String, Object>of("sub", "google-subject");
        var principal = new CustomOAuth2User(new DefaultOAuth2User(List.of(), attributes, "sub"),
                new AuthInfo(com.codeiary.domain.user.entity.enums.OAuthProvider.GOOGLE,
                        "google-subject", "google-regression@gmail.com", "기록자", true, true));
        var response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/auth/callback");
        var access = response.getCookie("access_token");
        assertThat(access).isNotNull();
        assertThat(response.getCookie("refresh_token")).isNotNull();
        assertThat(sessions.authenticate(access.getValue()).getEmail()).isEqualTo("google-regression@gmail.com");
        assertThat(connections.count()).isZero();
    }

    @Test
    void returnOnlyCurrentUsersConnection() throws Exception {
        mvc.perform(get("/github/connection")).andExpect(status().isUnauthorized());
        var first = users.saveAndFlush(UserFixture.create(Role.USER));
        var second = users.saveAndFlush(User.create("second@example.com", "두번째", Role.USER));
        var profile = ((CustomOAuth2User) githubAuthentication().getPrincipal()).getAuthInfo();
        githubConnections.connect(profile, new GitHubLinkContext(first.getId(), Instant.now()));
        mvc.perform(get("/github/connection").cookie(new Cookie("access_token",
                        sessions.createSession(first.getEmail()).accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.login").value("octocat"));
        mvc.perform(get("/github/connection").cookie(new Cookie("access_token",
                        sessions.createSession(second.getEmail()).accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.login").isEmpty());
    }

    @Test
    void disconnectOnlyCurrentUsersConnectionAndAllowRetry() throws Exception {
        mvc.perform(delete("/github/connection")).andExpect(status().isUnauthorized());
        var first = users.saveAndFlush(UserFixture.create(Role.USER));
        var second = users.saveAndFlush(User.create("second@example.com", "두번째", Role.USER));
        var profile = ((CustomOAuth2User) githubAuthentication().getPrincipal()).getAuthInfo();
        githubConnections.connect(profile, new GitHubLinkContext(first.getId(), Instant.now()));
        mvc.perform(delete("/github/connection").cookie(new Cookie("access_token",
                        sessions.createSession(second.getEmail()).accessToken())))
                .andExpect(status().isNoContent());
        assertThat(connections.findByUserId(first.getId())).isPresent();
        var cookie = new Cookie("access_token", sessions.createSession(first.getEmail()).accessToken());
        mvc.perform(delete("/github/connection").cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(delete("/github/connection").cookie(cookie)).andExpect(status().isNoContent());
        assertThat(connections.findByUserId(first.getId())).isEmpty();
        mvc.perform(get("/github/connection").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    void rejectGitHubCallbackWithoutLinkRequest() throws Exception {
        var response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, githubAuthentication());
        assertThat(response.getRedirectedUrl()).contains("error=oauth2");
        assertThat(response.getCookies()).isEmpty();
        assertThat(connections.count()).isZero();
    }

    @Test
    void rejectExpiredLinkAndAnotherUsersGitHubAccount() {
        var first = users.saveAndFlush(UserFixture.create(Role.USER));
        var second = users.saveAndFlush(User.create("second@example.com", "두번째", Role.USER));
        var profile = ((CustomOAuth2User) githubAuthentication().getPrincipal()).getAuthInfo();
        assertThatThrownBy(() -> githubConnections.connect(profile,
                new GitHubLinkContext(first.getId(), Instant.now().minus(Duration.ofMinutes(11)))))
                .isInstanceOf(RestApiException.class);
        githubConnections.connect(profile, new GitHubLinkContext(first.getId(), Instant.now()));
        githubConnections.connect(profile, new GitHubLinkContext(first.getId(), Instant.now()));
        assertThat(connections.count()).isEqualTo(1);
        assertThatThrownBy(() -> githubConnections.connect(profile,
                new GitHubLinkContext(second.getId(), Instant.now())))
                .isInstanceOf(RestApiException.class);
    }

    private OAuth2AuthorizationRequest authorization(GitHubLinkContext context) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://github.com/login/oauth/authorize")
                .clientId("test").redirectUri("http://localhost:8080/login/oauth2/code/github")
                .state("test-state").attributes(attributes -> {
                    attributes.put("registration_id", "github");
                    attributes.put(GitHubLinkContext.ATTRIBUTE, context);
                }).build();
    }

    private org.springframework.security.core.Authentication githubAuthentication() {
        var attributes = Map.<String, Object>of("id", 123L, "login", "octocat");
        var profile = OAuth2ResponseFactory.from("github", attributes);
        var principal = new CustomOAuth2User(new DefaultOAuth2User(List.of(), attributes, "id"),
                new AuthInfo(profile.getProvider(), profile.getSubject(), profile.getEmail(), profile.getName(),
                        profile.isEmailVerified(), profile.isEmailAuthoritative()));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }

    private Cookie cookie(Role role) {
        var user = users.saveAndFlush(UserFixture.create(role));
        return new Cookie("access_token", sessions.createSession(user.getEmail()).accessToken());
    }
}
