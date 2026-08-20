package com.spotpobre.backend.infrastructure.security.filter;

import com.spotpobre.backend.infrastructure.config.properties.RateLimitProperties;
import com.spotpobre.backend.infrastructure.security.ratelimit.FixedWindowRateLimiter;
import com.spotpobre.backend.infrastructure.web.exception.RestErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private FixedWindowRateLimiter rateLimiter;

    @Mock
    private RestErrorResponseWriter errorResponseWriter;

    private RateLimitProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties(
                true, 20, Duration.ofMinutes(1),
                List.of("/api/v1/auth/register", "/api/v1/auth/authenticate"),
                "X-Forwarded-For");
    }

    @Test
    void doFilter_withinLimit_continuesChain() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/authenticate");
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);

        RateLimitFilter filter = new RateLimitFilter(properties, rateLimiter, errorResponseWriter);
        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(errorResponseWriter);
    }

    @Test
    void doFilter_overLimit_writes429AndStopsChain() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/register");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(rateLimiter.tryAcquire(anyString())).thenReturn(false);

        RateLimitFilter filter = new RateLimitFilter(properties, rateLimiter, errorResponseWriter);
        filter.doFilter(request, response, filterChain);

        verify(errorResponseWriter).write(eq(request), eq(response), eq(HttpStatus.TOO_MANY_REQUESTS), eq("Too Many Requests"), anyString());
        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilter_usesFirstForwardedHeaderForClientIp() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/register");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9, 10.0.0.1");
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);

        RateLimitFilter filter = new RateLimitFilter(properties, rateLimiter, errorResponseWriter);
        filter.doFilter(request, response, filterChain);

        verify(rateLimiter).tryAcquire("203.0.113.9|POST|/api/v1/auth/register");
    }

    @Test
    void doFilter_whenDisabled_continuesChain() throws Exception {
        properties = new RateLimitProperties(
                false, 20, Duration.ofMinutes(1),
                List.of("/api/v1/auth/register", "/api/v1/auth/authenticate"),
                "X-Forwarded-For");

        RateLimitFilter filter = new RateLimitFilter(properties, rateLimiter, errorResponseWriter);
        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiter, errorResponseWriter);
    }

    @Test
    void doFilter_unprotectedPath_continuesChain() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/songs/123");

        RateLimitFilter filter = new RateLimitFilter(properties, rateLimiter, errorResponseWriter);
        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiter, errorResponseWriter);
    }
}