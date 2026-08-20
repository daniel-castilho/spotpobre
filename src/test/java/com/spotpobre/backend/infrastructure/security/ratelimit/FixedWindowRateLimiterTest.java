package com.spotpobre.backend.infrastructure.security.ratelimit;

import com.spotpobre.backend.infrastructure.config.properties.RateLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedWindowRateLimiterTest {

    private final MutableClock clock = new MutableClock();

    private FixedWindowRateLimiter limiter(int limit, Duration window) {
        RateLimitProperties properties = new RateLimitProperties(
                true, limit, window,
                java.util.List.of("/api/v1/auth/register", "/api/v1/auth/authenticate"),
                "X-Forwarded-For");
        return new FixedWindowRateLimiter(properties, clock);
    }

    @Test
    void tryAcquire_withinLimit_returnsTrue() {
        FixedWindowRateLimiter limiter = limiter(3, Duration.ofMinutes(1));

        assertTrue(limiter.tryAcquire("client|POST|/api/v1/auth/authenticate"));
        assertTrue(limiter.tryAcquire("client|POST|/api/v1/auth/authenticate"));
        assertTrue(limiter.tryAcquire("client|POST|/api/v1/auth/authenticate"));
    }

    @Test
    void tryAcquire_overLimit_returnsFalse() {
        FixedWindowRateLimiter limiter = limiter(2, Duration.ofMinutes(1));

        assertTrue(limiter.tryAcquire("client|POST|/api/v1/auth/authenticate"));
        assertTrue(limiter.tryAcquire("client|POST|/api/v1/auth/authenticate"));
        assertFalse(limiter.tryAcquire("client|POST|/api/v1/auth/authenticate"));
    }

    @Test
    void tryAcquire_afterWindowRollover_resetsCount() {
        FixedWindowRateLimiter limiter = limiter(1, Duration.ofMinutes(1));

        assertTrue(limiter.tryAcquire("client|POST|/api/v1/auth/register"));
        assertFalse(limiter.tryAcquire("client|POST|/api/v1/auth/register"));

        clock.advance(Duration.ofMinutes(1));

        assertTrue(limiter.tryAcquire("client|POST|/api/v1/auth/register"));
    }

    @Test
    void tryAcquire_distinctKeys_haveIndependentCounters() {
        FixedWindowRateLimiter limiter = limiter(1, Duration.ofMinutes(1));

        assertTrue(limiter.tryAcquire("client|POST|/api/v1/auth/register"));
        assertFalse(limiter.tryAcquire("client|POST|/api/v1/auth/register"));

        assertTrue(limiter.tryAcquire("client|POST|/api/v1/auth/authenticate"));
        assertTrue(limiter.tryAcquire("other|POST|/api/v1/auth/register"));
    }

    private static final class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}