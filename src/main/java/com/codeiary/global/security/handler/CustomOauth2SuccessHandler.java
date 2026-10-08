package com.codeiary.global.security.handler;

import com.codeiary.domain.users.entity.User;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.domain.auth.service.OAuthAccountService;
import com.codeiary.domain.auth.dto.TokenPair;
import com.codeiary.domain.auth.cookie.TokenCookieManager;
import com.codeiary.domain.auth.service.TokenSessionService;
import com.codeiary.global.security.dto.oauth2user.CustomOAuth2User;
import com.codeiary.global.security.exception.SecurityErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomOauth2SuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthAccountService oauthAccounts;
    private final TokenSessionService sessions;
    private final TokenCookieManager cookies;
    private final CustomOauth2FailureHandler failureHandler;

    @Value("${oauth2.redirect-home}")
    private String redirectHome;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        if (!(authentication.getPrincipal() instanceof CustomOAuth2User oauth2User)) {
            failureHandler.onAuthenticationFailure(request, response,
                    new BadCredentialsException(SecurityErrorCode.UNAUTHORIZED.getMessage()));
            return;
        }
        TokenPair pair;
        try {
            User user = oauthAccounts.getOrCreate(oauth2User.getAuthInfo());
            pair = sessions.createSession(user.getEmail());
        } catch (RestApiException exception) {
            failureHandler.onAuthenticationFailure(request, response,
                    new BadCredentialsException(exception.getErrorCode().getMessage(), exception));
            return;
        }
        cookies.writeTokens(response, pair);
        response.sendRedirect(redirectHome);
    }
}
