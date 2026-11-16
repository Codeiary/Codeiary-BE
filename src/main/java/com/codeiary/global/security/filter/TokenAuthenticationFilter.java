package com.codeiary.global.security.filter;

import com.codeiary.domain.auth.service.TokenService;
import com.codeiary.domain.user.entity.User;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.domain.auth.cookie.TokenCookieManager;
import com.codeiary.global.security.exception.SecurityErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public final class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final TokenCookieManager cookies;
    private final TokenService tokenService;
    private final SecurityErrorHandler errors;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.isEmpty()) {
            path = request.getRequestURI().substring(request.getContextPath().length());
        }
        return (path.startsWith("/oauth2/") && !path.equals("/oauth2/authorization/github"))
                || path.startsWith("/login/")
                || path.equals("/auth/reissue")
                || path.equals("/auth/logout");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        try {
            cookies.accessToken(request).ifPresent(rawToken -> authenticate(request, rawToken));
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            errors.handleServerError(response, exception);
            return;
        }
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String rawToken) {
        try {
            User user = tokenService.authenticate(rawToken);
            var authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
            );
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    user, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (RestApiException exception) {
            SecurityContextHolder.clearContext();
            if (exception.getErrorCode().getHttpStatus().is5xxServerError()) {
                throw exception;
            }
        }
    }
}
