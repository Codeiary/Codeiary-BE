package com.codeiary.global.security.token.cookie;

import com.codeiary.domain.users.entity.User;
import com.codeiary.domain.users.repository.UserRepository;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.token.exception.TokenErrorCode;
import com.codeiary.global.security.token.provider.JwtTokenProvider;
import com.codeiary.global.security.token.service.TokenService;

import io.jsonwebtoken.Claims;
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
    private final JwtTokenProvider tokens;
    private final UserRepository users;

    public TokenAuthenticationFilter(
            TokenService cookies,
            JwtTokenProvider tokens,
            UserRepository users
    ) {
        this.cookies = cookies;
        this.tokens = tokens;
        this.users = users;
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
            Claims claims = tokens.validateAccessToken(rawToken);
            User user = users.findById(Long.valueOf(claims.getSubject()))
                    .filter(User::isEnabled)
                    .orElseThrow(() -> new RestApiException(TokenErrorCode.TOKEN_INVALID));
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
