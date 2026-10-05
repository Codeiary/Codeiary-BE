package com.codeiary.global.auth.filter;

import com.codeiary.domain.users.entity.User;
import com.codeiary.global.auth.service.AuthService;
import com.codeiary.global.auth.exception.SecurityErrorHandler;
import com.codeiary.global.auth.service.JwtTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokens;
    private final AuthService authService;
    private final SecurityErrorHandler errors;

    public JwtAuthenticationFilter(JwtTokenService jwtTokens, AuthService authService,
                                   SecurityErrorHandler errors) {
        this.jwtTokens = jwtTokens;
        this.authService = authService;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer", 0, 6)) {
            try {
                if (Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION)).size() != 1
                        || header.length() <= 7 || header.charAt(6) != ' ') {
                    throw new JwtException("Invalid authorization header");
                }
                Claims claims = jwtTokens.validateAccessToken(header.substring(7));
                User user = authService.loadActiveUser(Long.valueOf(claims.getSubject()));

                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (JwtException | IllegalArgumentException | AuthenticationException exception) {
                SecurityContextHolder.clearContext();
                errors.commence(request, response, new BadCredentialsException("Invalid access token"));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
