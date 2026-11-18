package com.codeiary.global.security.handler;

import com.codeiary.domain.auth.cookie.TokenCookieManager;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.exception.SecurityErrorHandler;
import com.codeiary.global.security.repository.OAuth2RequestRepository;
import com.codeiary.global.security.service.OAuthAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomOauth2SuccessHandler implements AuthenticationSuccessHandler {

  private final OAuthAccountService accounts;
  private final TokenCookieManager tokenCookieManager;
  private final SecurityErrorHandler securityErrorHandler;

  @Value("${oauth2.redirect-home}")
  private String redirectHome;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      Authentication authentication)
      throws IOException {
    try {
      var authorization =
          (OAuth2AuthorizationRequest)
              httpServletRequest.getAttribute(OAuth2RequestRepository.COMPLETED_REQUEST);
      accounts
          .complete(authentication, authorization)
          .ifPresent(pair -> tokenCookieManager.writeTokens(httpServletResponse, pair));
    } catch (RestApiException exception) {
      securityErrorHandler.handleOAuthFailure(httpServletRequest, httpServletResponse, exception);
      return;
    }
    httpServletResponse.sendRedirect(redirectHome);
  }
}
