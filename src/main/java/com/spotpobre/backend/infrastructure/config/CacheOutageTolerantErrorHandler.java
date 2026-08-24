package com.spotpobre.backend.infrastructure.config;

import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import lombok.extern.slf4j.Slf4j;

/**
 * Cache error handling policy (S6): Redis is a best-effort read-through cache, never a
 * dependency of the request path. A Redis outage must degrade to direct lookups instead of
 * failing every authenticated request — readiness deliberately does not gate on Redis
 * ({@code application.yaml} health group comment).
 *
 * <p>Wired via {@link CachingConfigurer#errorHandler()}: read failures are treated as a miss
 * (the loader runs against the source of truth); failures while writing, evicting or clearing
 * are logged and swallowed so they cannot fail a request that already succeeded against storage.
 * Stale-entry exposure after a swallowed evict is bounded by the cache TTL (5 minutes for
 * {@link CacheConfig#USER_CACHE}). Policy: this layer never throws — a corrupted entry or an
 * unexpected cache bug must degrade exactly like an outage (loud WARN with stack trace), because
 * authentication availability beats cache hygiene; the next successful write self-heals the
 * entry.</p>
 */
@Slf4j
public class CacheOutageTolerantErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(final RuntimeException exception, final Cache cache, final Object key) {
        if (isInfrastructureOutage(exception)) {
            log.warn("Cache '{}' unavailable ({}); degrading to source lookup for key '{}'",
                    cache.getName(), rootCauseClass(exception).getSimpleName(),
                    com.spotpobre.backend.infrastructure.common.Redaction.digest(String.valueOf(key)));
            return;
        }
        // Corrupt entries and unexpected cache bugs must not brick authentication either:
        // treat as a miss with the stack trace preserved for investigation.
        log.warn("Cache '{}' read failed for key '{}'; treating as miss", cache.getName(),
                com.spotpobre.backend.infrastructure.common.Redaction.digest(String.valueOf(key)), exception);
    }

    @Override
    public void handleCachePutError(final RuntimeException exception, final Cache cache,
                                    final Object key, final Object value) {
        if (isInfrastructureOutage(exception)) {
            log.warn("Cache '{}' unavailable ({}); skipping write for key '{}'",
                    cache.getName(), rootCauseClass(exception).getSimpleName(),
                    com.spotpobre.backend.infrastructure.common.Redaction.digest(String.valueOf(key)));
            return;
        }
        log.warn("Cache '{}' write failed for key '{}'; skipping", cache.getName(),
                com.spotpobre.backend.infrastructure.common.Redaction.digest(String.valueOf(key)), exception);
    }

    @Override
    public void handleCacheEvictError(final RuntimeException exception, final Cache cache, final Object key) {
        log.warn("Cache '{}' evict failed for key '{}'; stale entry expires within TTL",
                cache.getName(), key, exception);
    }

    @Override
    public void handleCacheClearError(final RuntimeException exception, final Cache cache) {
        log.warn("Cache '{}' clear failed; entries expire within TTL", cache.getName(), exception);
    }

    private boolean isInfrastructureOutage(final RuntimeException exception) {
        return exception instanceof RedisConnectionFailureException
                || exception instanceof RedisSystemException
                || exception instanceof QueryTimeoutException;
    }

    private Class<?> rootCauseClass(final Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass();
    }
}
