package com.spotpobre.backend.infrastructure.security.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotpobre.backend.infrastructure.config.properties.RateLimitProperties;
import com.spotpobre.backend.infrastructure.security.ratelimit.ClientAddressResolver;
import com.spotpobre.backend.infrastructure.security.ratelimit.RateLimitEvaluation;
import com.spotpobre.backend.infrastructure.security.ratelimit.RateLimitKeyEncoder;
import com.spotpobre.backend.infrastructure.security.ratelimit.RateLimitService;
import com.spotpobre.backend.infrastructure.web.exception.RestErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Anonymous rate limiting (spec section 8.3): register/authenticate IP-wide and
 * IP+normalized-e-mail buckets, evaluated BEFORE any Argon2 or idempotency work. The e-mail
 * bucket reads the cached JSON body; the raw address never leaves this layer (HMAC keys).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnonymousRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final RateLimitService rateLimitService;
    private final ClientAddressResolver clientAddressResolver;
    private final RateLimitKeyEncoder keyEncoder;
    private final RestErrorResponseWriter errorResponseWriter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !properties.enabled()
                || !properties.anonymousPoliciesPaths().contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull final HttpServletRequest request,
                                    @NonNull final HttpServletResponse response,
                                    @NonNull final FilterChain filterChain)
            throws ServletException, IOException {
        CachedBodyRequestWrapper wrapped = new CachedBodyRequestWrapper(request);
        String clientIp = clientAddressResolver.resolve(wrapped);
        String email = extractEmail(wrapped.bodyAsString());

        boolean register = request.getRequestURI().endsWith("/register");
        RateLimitEvaluation evaluation = register
                ? rateLimitService.checkRegister(clientIp, email)
                : rateLimitService.checkAuthenticate(clientIp, email);

        if (!evaluation.allowed()) {
            applyHeaders(response, evaluation);
            response.setHeader("Retry-After", String.valueOf(evaluation.retryAfterSeconds()));
            errorResponseWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                    "Too Many Requests",
                    "Rate limit exceeded. Please try again later.");
            return;
        }
        applyHeaders(response, evaluation);
        filterChain.doFilter(wrapped, response);
    }

    /** Extracts and normalizes the e-mail without ever logging or storing the raw value. */
    private String extractEmail(final String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode emailNode = node.get("email");
            if (emailNode != null && !emailNode.isNull()) {
                return emailNode.asText().trim().toLowerCase(Locale.ROOT);
            }
        } catch (Exception e) {
            log.debug("Rate-limit body inspection skipped: malformed JSON body");
        }
        return "";
    }

    static void applyHeaders(final HttpServletResponse response, final RateLimitEvaluation evaluation) {
        response.setHeader("RateLimit-Limit", String.valueOf(evaluation.limit()));
        response.setHeader("RateLimit-Remaining", String.valueOf(evaluation.remaining()));
        response.setHeader("RateLimit-Reset", String.valueOf(evaluation.resetSeconds()));
    }
}
