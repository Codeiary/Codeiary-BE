package com.codeiary.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public final class MDCFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID = "requestId";
    private final List<String> warnErrorOnlyApis;

    public MDCFilter(List<String> warnErrorOnlyApis) {
        this.warnErrorOnlyApis = warnErrorOnlyApis;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String api = request.getMethod() + " " + request.getRequestURI();
        String requestId = UUID.randomUUID().toString();

        MDC.put(REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        if (!warnErrorOnlyApis.contains(api)) {
            log.atInfo()
                    .addKeyValue("event", "api_start")
                    .addKeyValue("api", api)
                    .log("API request started");
        }

        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            int status = response.getStatus();
            if (status >= 500 || status >= 400 || !warnErrorOnlyApis.contains(api)) {
                var event = status >= 500 ? log.atError() : status >= 400 ? log.atWarn() : log.atInfo();
                event.addKeyValue("event", "api_end")
                        .addKeyValue("api", api)
                        .addKeyValue("status", status)
                        .addKeyValue("durationMs", durationMs)
                        .log("API request completed");
            }
            MDC.remove(REQUEST_ID);
        }
    }
}
