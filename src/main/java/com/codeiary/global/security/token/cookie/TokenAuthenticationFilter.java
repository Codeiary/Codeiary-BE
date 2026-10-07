package com.codeiary.global.security.token.cookie;

import com.codeiary.domain.users.entity.User;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.token.service.TokenService;
import com.codeiary.global.security.token.service.TokenSessionService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService cookies;
    private final TokenSessionService sessions;

    public TokenAuthenticationFilter(
            TokenService cookies,
            TokenSessionService sessions
    ) {
        this.cookies = cookies;
        this.sessions = sessions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/oauth2/") || path.startsWith("/login/")
                || path.equals("/api/auth/reissue") || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/logout");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        cookies.accessToken(request).ifPresent(rawToken -> authenticate(request, rawToken));
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String rawToken) {
        try {
            User user = sessions.authenticate(rawToken);
            var authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
            );
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    user, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (RestApiException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
        }
    }
}
