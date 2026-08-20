package com.spotpobre.backend.infrastructure.security.ratelimit;

import com.spotpobre.backend.infrastructure.config.properties.RateLimitProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight fixed-window rate limiter, in-memory and thread-safe.
 *
 * <p>Keeps a per-key counter for the current time window. When the window rolls over the counter
 * resets; a request is allowed while {@code count <= limit}. Stale windows are purged lazily to keep
 * memory bounded. Intentionally dependency-free: no Redis required (the test suite runs without it),
 * which matches the basic protection this class provides.
 */
@Component
public class FixedWindowRateLimiter {

    private static final long PURGE_THRESHOLD = 10_000;

    private final RateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong purgeCounter = new AtomicLong();

    public FixedWindowRateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Attempts to acquire a slot for {@code key} within the configured window.
     *
     * @param key the client identity (e.g. {@code ip|method|path})
     * @return {@code true} if the request is within the configured limit, {@code false} otherwise
     */
    public boolean tryAcquire(String key) {
        long windowMillis = properties.window().toMillis();
        long now = clock.millis();
        long windowStart = now - (now % windowMillis);

        Window updated = windows.compute(key, (ignored, current) ->
                current == null || current.windowStart() != windowStart
                        ? new Window(windowStart, 1L)
                        : new Window(windowStart, current.count() + 1L));

        if (purgeCounter.incrementAndGet() % 64 == 0) {
            purgeExpired(now);
        }
        return updated.count() <= properties.limit();
    }

    private void purgeExpired(long now) {
        if (windows.size() < PURGE_THRESHOLD) {
            return;
        }
        long windowMillis = properties.window().toMillis();
        long oldest = now - windowMillis;
        windows.entrySet().removeIf(entry -> entry.getValue().windowStart() < oldest);
    }

    private record Window(long windowStart, long count) {
    }
}