package com.codeiary.global.security.handler;

import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.token.dto.TokenPair;
import com.codeiary.global.security.token.service.TokenService;
import com.codeiary.global.security.token.service.TokenSessionService;
import com.codeiary.global.security.dto.oauth2user.CustomOAuth2User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomOauth2SuccessHandler implements AuthenticationSuccessHandler {

    private final TokenSessionService sessions;
    private final TokenService cookieService;
    private final CustomOauth2FailureHandler failureHandler;

    @Value("${oauth2.redirect-home:http://localhost:5173/auth/callback}")
    private String redirectHome;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        if (!(authentication.getPrincipal() instanceof CustomOAuth2User oauth2User)) {
            failureHandler.onAuthenticationFailure(request, response,
                    new BadCredentialsException("Unavailable OAuth user"));
            return;
        }
        String email = oauth2User.getEmail();
        if (email == null || email.isBlank()) {
            failureHandler.onAuthenticationFailure(request, response,
                    new BadCredentialsException("Missing OAuth email"));
            return;
        }
        TokenPair pair;
        try {
            pair = sessions.createSession(email.toLowerCase(Locale.ROOT));
        } catch (RestApiException exception) {
            failureHandler.onAuthenticationFailure(request, response,
                    new BadCredentialsException("Unavailable user", exception));
            return;
        }
        cookieService.writeTokens(response, pair);
        response.sendRedirect(redirectHome);
    }
}
