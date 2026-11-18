package com.codeiary.global.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.codeiary.domain.auth.cookie.TokenCookieManager;
import com.codeiary.domain.auth.exception.TokenErrorCode;
import com.codeiary.domain.auth.fixture.TokenFixture;
import com.codeiary.domain.auth.service.TokenService;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.exception.SecurityErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TokenAuthenticationFilterTest {

  @Mock private TokenService tokenService;

  @Mock private FilterChain chain;

  @Mock private SecurityErrorHandler errors;

  private TokenAuthenticationFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    TokenCookieManager tokenCookieManager = TokenFixture.cookies("");
    filter = new TokenAuthenticationFilter(tokenCookieManager, tokenService, errors);
    request = new MockHttpServletRequest();
    request.setServletPath("/users/me");
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
    given(tokenService.authenticate("valid-access")).willReturn(user);

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
    then(tokenService).should().authenticate("valid-access");
    then(chain).should().doFilter(request, response);
  }

  @Test
  @DisplayName("유효하지 않거나 폐기된 토큰의 인증을 지우고 요청을 계속할 수 있다.")
  void clearRejectedAuthentication() throws Exception {
    // given
    request.setCookies(new Cookie("access_token", "rejected-access"));
    setExistingAuthentication();
    given(tokenService.authenticate("rejected-access"))
        .willThrow(new RestApiException(TokenErrorCode.TOKEN_REVOKED));

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    then(tokenService).should().authenticate("rejected-access");
    then(chain).should().doFilter(request, response);
  }

  @Test
  @DisplayName("블랙리스트 조회가 실패하면 500을 응답하고 요청을 멈출 수 있다.")
  void handleBlacklistLookupFailure() throws Exception {
    request.setCookies(new Cookie("access_token", "valid-access"));
    RestApiException restApiException = new RestApiException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    given(tokenService.authenticate("valid-access")).willThrow(restApiException);

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    then(errors).should().handleServerError(response, restApiException);
    then(chain).shouldHaveNoInteractions();
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
    then(tokenService).shouldHaveNoInteractions();
    then(chain).should().doFilter(request, response);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/auth/logout", "/auth/reissue"})
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
    then(tokenService).shouldHaveNoInteractions();
    then(chain).should().doFilter(request, response);
  }

  private void setExistingAuthentication() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("previous-user", null, List.of()));
  }
}
