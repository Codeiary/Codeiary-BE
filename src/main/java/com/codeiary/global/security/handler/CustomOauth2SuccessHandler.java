package com.codeiary.global.security.handler;

import com.codeiary.domain.users.entity.User;
import com.codeiary.domain.users.exception.UserErrorCode;
import com.codeiary.domain.users.repository.UserRepository;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.token.provider.JwtTokenProvider;
import com.codeiary.global.security.token.service.TokenService;
import com.codeiary.global.security.dto.oauth2user.CustomOAuth2User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomOauth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final TokenService cookieService;

    @Value("${oauth2.redirect-home:http://localhost:5173/}")
    private String redirectHome;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        if (!(authentication.getPrincipal() instanceof CustomOAuth2User oauth2User)) {
            throw new RestApiException(UserErrorCode.NOT_FOUND_USER);
        }
        String email = oauth2User.getEmail();
        if (email == null || email.isBlank()) {
            throw new RestApiException(UserErrorCode.NOT_FOUND_USER);
        }
        email = email.toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RestApiException(UserErrorCode.NOT_FOUND_USER));

        UUID sessionId = UUID.randomUUID();
        String accessToken = tokenProvider.generateAccessToken(
                user.getId(), user.getRole().name(), sessionId);
        String refreshToken = tokenProvider.generateRefreshToken(
                user.getId(), user.getRole().name(), sessionId);

        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.accessToken(accessToken).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.refreshToken(refreshToken).toString());
        response.sendRedirect(redirectHome);
    }
}
