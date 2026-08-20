package com.spotpobre.backend.infrastructure.security.filter;

import com.spotpobre.backend.infrastructure.config.properties.RateLimitProperties;
import com.spotpobre.backend.infrastructure.security.ratelimit.FixedWindowRateLimiter;
import com.spotpobre.backend.infrastructure.web.exception.RestErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Applies basic per-client rate limiting to sensitive endpoints (default: the auth endpoints).
 *
 * <p>The client key is derived from the first value of {@code X-Forwarded-For} when present (the
 * proxy/ALB set it in production), otherwise from the remote address. When the configured limit is
 * exceeded a {@code 429 Too Many Requests} is written using the canonical {@link ErrorResponse}
 * envelope via {@link RestErrorResponseWriter}. Registered before the JWT filter so throttling happens
 * before any token work on the protected paths.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties rateLimitProperties;
    private final FixedWindowRateLimiter rateLimiter;
    private final RestErrorResponseWriter errorResponseWriter;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !rateLimitProperties.enabled() || !isProtectedPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String key = clientIp(request) + "|" + request.getMethod() + "|" + request.getRequestURI();
        if (!rateLimiter.tryAcquire(key)) {
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too Many Requests",
                    "Rate limit exceeded. Please try again later."
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isProtectedPath(String uri) {
        return rateLimitProperties.paths().contains(uri);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(rateLimitProperties.clientIpHeader());
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}