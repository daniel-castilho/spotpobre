package com.spotpobre.backend;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for full-flow (RANDOM_PORT) integration tests that exercise real HTTP endpoints.
 *
 * <p>Flow ITs share one cached application context and hammer the auth endpoints from a single
 * loopback client; the small dev default (20/min) would spuriously 429 them. This base therefore
 * neutralises rate limiting for its subclasses only. It lives in a separate class — and NOT in
 * {@link AbstractIntegrationTest} — because {@code @DynamicPropertySource} values take precedence
 * over subclass {@code @TestPropertySource} values; keeping it here lets rate-limit-specific ITs
 * (e.g. {@code RateLimitFlowIT}) extend the plain base and control {@code rate-limit.*} themselves.
 */
public abstract class AbstractFlowIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void neutralizeRateLimit(DynamicPropertyRegistry registry) {
        registry.add("rate-limit.limit", () -> "100000");
    }
}
