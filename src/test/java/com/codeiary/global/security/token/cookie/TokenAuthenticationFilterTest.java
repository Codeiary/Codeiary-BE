package com.codeiary.global.security.token.cookie;

import com.codeiary.global.security.token.fixture.TokenFixture;
import com.codeiary.domain.users.entity.User;
import com.codeiary.domain.users.entity.enums.Role;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.token.exception.TokenErrorCode;
import com.codeiary.global.security.token.service.TokenService;
import com.codeiary.global.security.token.service.TokenSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TokenAuthenticationFilterTest {

    @Mock
    private TokenSessionService sessions;

    @Mock
    private FilterChain chain;

    private TokenAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        TokenService cookies = TokenFixture.cookies("");
        filter = new TokenAuthenticationFilter(cookies, sessions);
        request = new MockHttpServletRequest();
        request.setServletPath("/api/users/me");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Access Token 쿠키로 사용자와 권한을 인증할 수 있다.")
    void authenticateUserFromCookie() throws Exception {
        // given
        User user = UserFixture.createWithId(Role.ADMIN);
        request.setCookies(new Cookie("access_token", "valid-access"));
        given(sessions.authenticate("valid-access")).willReturn(user);

        // when
        filter.doFilter(request, response, chain);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isSameAs(user);
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        then(sessions).should().authenticate("valid-access");
        then(chain).should().doFilter(request, response);
    }

    @ParameterizedTest
    @EnumSource(value = TokenErrorCode.class, names = {"TOKEN_INVALID", "TOKEN_REVOKED"})
    @DisplayName("유효하지 않거나 폐기된 토큰의 인증을 지우고 요청을 계속할 수 있다.")
    void clearRejectedAuthentication(TokenErrorCode errorCode) throws Exception {
        // given
        request.setCookies(new Cookie("access_token", "rejected-access"));
        setExistingAuthentication();
        given(sessions.authenticate("rejected-access"))
                .willThrow(new RestApiException(errorCode));

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(sessions).should().authenticate("rejected-access");
        then(chain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("토큰 형식 오류의 인증을 지우고 요청을 계속할 수 있다.")
    void clearMalformedAuthentication() throws Exception {
        // given
        request.setCookies(new Cookie("access_token", "malformed-access"));
        setExistingAuthentication();
        given(sessions.authenticate("malformed-access"))
                .willThrow(new IllegalArgumentException("Invalid session identifier"));

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(chain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("쿠키가 없으면 Bearer 헤더로 인증하지 않고 요청을 계속할 수 있다.")
    void continueWithoutAccessCookie() throws Exception {
        // given
        request.addHeader("Authorization", "Bearer legacy-access");

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(sessions).shouldHaveNoInteractions();
        then(chain).should().doFilter(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/logout", "/api/auth/reissue", "/api/auth/refresh"})
    @DisplayName("로그아웃과 재발급은 Access Token 인증 없이 처리할 수 있다.")
    void skipAccessAuthenticationForSessionEndpoints(String path) throws Exception {
        // given
        request.setMethod("POST");
        request.setServletPath(path);
        request.setCookies(new Cookie("access_token", "expired-access"));

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(sessions).shouldHaveNoInteractions();
        then(chain).should().doFilter(request, response);
    }

    private void setExistingAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("previous-user", null, List.of()));
    }
}
