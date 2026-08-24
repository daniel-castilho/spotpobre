package com.spotpobre.backend.infrastructure.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Correlation/request IDs through logs (spec S22): every request carries a short
 * {@code requestId} in the MDC (rendered by the log pattern), echoed back via the
 * {@code X-Request-Id} response header. An incoming well-formed {@code X-Request-Id} is
 * reused so callers can correlate across services; anything malformed is replaced.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    static final String MDC_KEY = "requestId";
    private static final int MAX_INCOMING_LENGTH = 64;
    private static final String SAFE = "[A-Za-z0-9._-]{8,64}";

    @Override
    protected void doFilterInternal(@NonNull final HttpServletRequest request,
                                    @NonNull final HttpServletResponse response,
                                    @NonNull final FilterChain filterChain)
            throws ServletException, IOException {
        try {
            MDC.put(MDC_KEY, resolveRequestId(request));
            response.setHeader(HEADER, MDC.get(MDC_KEY));
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolveRequestId(final HttpServletRequest request) {
        String incoming = request.getHeader(HEADER);
        if (incoming != null && incoming.matches(SAFE) && incoming.length() <= MAX_INCOMING_LENGTH) {
            return incoming;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
