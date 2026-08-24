package com.spotpobre.backend.infrastructure.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotpobre.backend.infrastructure.config.properties.RateLimitProperties;
import com.spotpobre.backend.infrastructure.security.ratelimit.ClientAddressResolver;
import com.spotpobre.backend.infrastructure.security.ratelimit.RateLimitEvaluation;
import com.spotpobre.backend.infrastructure.security.ratelimit.RateLimitKeyEncoder;
import com.spotpobre.backend.infrastructure.security.ratelimit.RateLimitService;
import com.spotpobre.backend.infrastructure.web.exception.RestErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnonymousRateLimitFilterTest {

    private RateLimitService rateLimitService;
    private ClientAddressResolver clientAddressResolver;
    private AnonymousRateLimitFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties(
                true, "secret", 20, Duration.ofHours(1), 5, Duration.ofHours(1),
                100, Duration.ofMinutes(15), 10, Duration.ofMinutes(15),
                20, Duration.ofMinutes(1), 40, Duration.ofHours(1),
                60, Duration.ofMinutes(1), 120, Duration.ofHours(1),
                120, Duration.ofMinutes(1), 1, Duration.ofMinutes(2),
                List.of("/api/v1/auth/register", "/api/v1/auth/authenticate"),
                "/api/v1/albums/{albumId}/songs",
                "/api/v1/albums/{albumId}/songs/{songId}/confirm",
                "/api/v1/songs/search",
                List.of("127.0.0.0/8"),
                "X-Forwarded-For");
        rateLimitService = Mockito.mock(RateLimitService.class);
        clientAddressResolver = Mockito.mock(ClientAddressResolver.class);
        filter = new AnonymousRateLimitFilter(properties, rateLimitService,
                clientAddressResolver,
                new RateLimitKeyEncoder("secret"),
                Mockito.mock(RestErrorResponseWriter.class));
    }

    private MockHttpServletRequest registerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/api/v1/auth/register");
        request.setRequestURI("/api/v1/auth/register");
        request.setContentType("application/json");
        request.setContent("{\"name\":\"N\",\"email\":\"User@Example.com\",\"password\":\"x\"}"
                .getBytes());
        return request;
    }

    @Test
    void doFilter_allowedRequest_setsRateLimitHeadersAndPassesCachedBodyDownstream() throws Exception {
        request = registerRequest();
        response = new MockHttpServletResponse();
        when(clientAddressResolver.resolve(any())).thenReturn("127.0.0.1");
        when(rateLimitService.checkRegister(eq("127.0.0.1"), eq("user@example.com")))
                .thenReturn(new RateLimitEvaluation(true, 5, 4, 3600, 0));
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(CachedBodyRequestWrapper.class), any(HttpServletResponse.class));
        assertEquals("5", response.getHeader("RateLimit-Limit"));
        assertEquals("4", response.getHeader("RateLimit-Remaining"));
        assertEquals("3600", response.getHeader("RateLimit-Reset"));
    }

    @Test
    void doFilter_blockedRequest_writesCanonical429WithRetryAfter() throws Exception {
        request = registerRequest();
        response = new MockHttpServletResponse();
        RestErrorResponseWriter writer = Mockito.mock(RestErrorResponseWriter.class);
        filter = new AnonymousRateLimitFilter(propertiesWithPaths(), rateLimitService,
                clientAddressResolver, new RateLimitKeyEncoder("secret"), writer);
        when(clientAddressResolver.resolve(any())).thenReturn("127.0.0.1");
        when(rateLimitService.checkRegister(anyString(), anyString()))
                .thenReturn(new RateLimitEvaluation(false, 5, 0, 42, 42));

        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(writer).write(any(), any(), eq(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS),
                eq("Too Many Requests"), anyString());
        assertEquals("42", response.getHeader("Retry-After"));
        assertEquals("5", response.getHeader("RateLimit-Limit"));
    }

    @Test
    void doFilter_nonPolicyPath_skipped() {
        MockHttpServletRequest other = new MockHttpServletRequest("GET", "/api/v1/songs/search");
        other.setRequestURI("/api/v1/songs/search");

        assertTrue(filter.shouldNotFilter(other));
    }

    private RateLimitProperties propertiesWithPaths() {
        return new RateLimitProperties(
                true, "secret", 20, Duration.ofHours(1), 5, Duration.ofHours(1),
                100, Duration.ofMinutes(15), 10, Duration.ofMinutes(15),
                20, Duration.ofMinutes(1), 40, Duration.ofHours(1),
                60, Duration.ofMinutes(1), 120, Duration.ofHours(1),
                120, Duration.ofMinutes(1), 1, Duration.ofMinutes(2),
                List.of("/api/v1/auth/register", "/api/v1/auth/authenticate"),
                "/api/v1/albums/{albumId}/songs",
                "/api/v1/albums/{albumId}/songs/{songId}/confirm",
                "/api/v1/songs/search",
                List.of("127.0.0.0/8"),
                "X-Forwarded-For");
    }
}
