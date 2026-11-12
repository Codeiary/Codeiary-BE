package com.codeiary.global.security.handler;

import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.exception.SecurityErrorHandler;
import com.codeiary.global.security.repository.OAuth2RequestRepository;

import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import com.codeiary.domain.auth.cookie.TokenCookieManager;
import com.codeiary.global.security.service.OAuthAccountService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomOauth2SuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthAccountService accounts;
    private final TokenCookieManager cookies;
    private final SecurityErrorHandler errors;

    @Value("${oauth2.redirect-home}")
    private String redirectHome;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        try {
            var authorization = (OAuth2AuthorizationRequest) request.getAttribute(OAuth2RequestRepository.COMPLETED_REQUEST);
            accounts.complete(authentication, authorization)
                    .ifPresent(pair -> cookies.writeTokens(response, pair));
        } catch (RestApiException exception) {
            errors.handleOAuthFailure(request, response, exception);
            return;
        }
        response.sendRedirect(redirectHome);
    }
}
