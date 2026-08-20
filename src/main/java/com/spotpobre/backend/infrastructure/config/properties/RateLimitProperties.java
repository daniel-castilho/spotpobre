package com.spotpobre.backend.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Externalised configuration for basic per-client rate limiting (Twelve-Factor factor 3).
 *
 * <p>Defaults are baked here so the application boots safely without configuration; operators
 * override them via environment variables in production (see {@code application-prod.yaml}).
 * Rate limiting is intentionally implemented in-memory (fixed window) — lightweight, dependency-free
 * and consistent with the existing single-node deployment model.
 */
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("20") int limit,
        @DefaultValue("1m") Duration window,
        @DefaultValue({"/api/v1/auth/register", "/api/v1/auth/authenticate"}) List<String> paths,
        @DefaultValue("X-Forwarded-For") String clientIpHeader
) {
}