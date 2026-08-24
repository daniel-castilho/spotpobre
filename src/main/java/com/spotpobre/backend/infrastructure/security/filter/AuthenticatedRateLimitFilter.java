package com.spotpobre.backend.infrastructure.security.filter;

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
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriTemplate;

import java.io.IOException;

/**
 * Authenticated rate limiting (spec section 8.3): upload initiate/confirm user and user+album
 * buckets, search user bucket with trusted-IP fallback. Runs after JWT authentication so the
 * principal is available; still precedes idempotency claims and business side effects.
 * Idempotent replays deliberately consume capacity.
 */
@Component
public class AuthenticatedRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final RateLimitService rateLimitService;
    private final ClientAddressResolver clientAddressResolver;
    private final RateLimitKeyEncoder keyEncoder;
    private final RestErrorResponseWriter errorResponseWriter;
    private final UriTemplate uploadInitiateTemplate;
    private final UriTemplate uploadConfirmTemplate;

    public AuthenticatedRateLimitFilter(final RateLimitProperties properties,
                                        final RateLimitService rateLimitService,
                                        final ClientAddressResolver clientAddressResolver,
                                        final RateLimitKeyEncoder keyEncoder,
                                        final RestErrorResponseWriter errorResponseWriter) {
        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.clientAddressResolver = clientAddressResolver;
        this.keyEncoder = keyEncoder;
        this.errorResponseWriter = errorResponseWriter;
        this.uploadInitiateTemplate = new UriTemplate(properties.uploadInitiatePathTemplate());
        this.uploadConfirmTemplate = new UriTemplate(properties.uploadConfirmPathTemplate());
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!properties.enabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        return !uri.equals(properties.searchPath())
                && !(request.getMethod().equals("POST") && uploadInitiateTemplate.matches(uri))
                && !(request.getMethod().equals("POST") && uploadConfirmTemplate.matches(uri));
    }

    @Override
    protected void doFilterInternal(@NonNull final HttpServletRequest request,
                                    @NonNull final HttpServletResponse response,
                                    @NonNull final FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actorSubject = actorSubject(request);
        String uri = request.getRequestURI();

        RateLimitEvaluation evaluation;
        try {
            if (uri.equals(properties.searchPath())) {
                evaluation = rateLimitService.checkSearch(actorSubject);
            } else {
                String albumId = albumIdFor(uri);
                evaluation = uploadInitiateTemplate.matches(uri)
                        ? rateLimitService.checkUploadInitiate(actorSubject, albumId)
                        : rateLimitService.checkUploadConfirm(actorSubject, albumId);
            }
        } catch (com.spotpobre.backend.domain.common.RateLimiterUnavailableException e) {
            // Filters sit outside DispatcherServlet: write the canonical 503 here instead of
            // relying on @ExceptionHandler (spec section 8.4 - never claim a limit was hit).
            errorResponseWriter.write(request, response,
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Service Unavailable",
                    "Rate-limit backend temporarily unavailable.");
            return;
        }

        if (!evaluation.allowed()) {
            AnonymousRateLimitFilter.applyHeaders(response, evaluation);
            response.setHeader("Retry-After", String.valueOf(evaluation.retryAfterSeconds()));
            errorResponseWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                    "Too Many Requests",
                    "Rate limit exceeded. Please try again later.");
            return;
        }
        AnonymousRateLimitFilter.applyHeaders(response, evaluation);
        filterChain.doFilter(request, response);
    }

    /** HMAC'd identity: authenticated principal, else trusted resolved IP for search fallback. */
    private String actorSubject(final HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {
            return keyEncoder.sha256Hex("user:" + authentication.getName());
        }
        return clientAddressResolver.resolve(request);
    }

    private String albumIdFor(final String uri) {
        var vars = uploadInitiateTemplate.match(uri);
        if (vars == null || vars.isEmpty()) {
            vars = uploadConfirmTemplate.match(uri);
        }
        Object albumId = vars == null ? null : vars.get("albumId");
        return albumId == null ? "" : albumId.toString();
    }
}
