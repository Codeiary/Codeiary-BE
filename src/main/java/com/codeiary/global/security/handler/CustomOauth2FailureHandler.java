package com.codeiary.global.security.handler;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomOauth2FailureHandler implements AuthenticationFailureHandler {

    @Value("${oauth2.redirect-home}")
    private String redirectHome;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        String redirectUrl = UriComponentsBuilder.fromUriString(redirectHome)
                .queryParam("error", "oauth2")
                .build()
                .toUriString();
        response.sendRedirect(redirectUrl);
    }
}
