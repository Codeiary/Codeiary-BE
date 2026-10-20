package com.codeiary.global.logging;

import com.codeiary.domain.user.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public final class MDCFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String TRACE_ID = "traceId";
    private static final String USER_ID = "userId";
    private static final String ANONYMOUS = "anonymous";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String api = request.getMethod() + " " + request.getRequestURI();
        String traceId = UUID.randomUUID().toString();

        MDC.put(TRACE_ID, traceId);
        MDC.put(USER_ID, currentUserId());
        response.setHeader(REQUEST_ID_HEADER, traceId);
        log.info("[API START] {}", api);

        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            int status = response.getStatus();
            if (status >= 500) {
                log.error("[API END] {} status={} durationMs={}", api, status, durationMs);
            } else if (status >= 400) {
                log.warn("[API END] {} status={} durationMs={}", api, status, durationMs);
            } else {
                log.info("[API END] {} status={} durationMs={}", api, status, durationMs);
            }
            MDC.remove(TRACE_ID);
            MDC.remove(USER_ID);
        }
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId().toString();
        }
        return ANONYMOUS;
    }
}
