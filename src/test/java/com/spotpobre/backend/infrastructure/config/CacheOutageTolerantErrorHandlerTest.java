package com.spotpobre.backend.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

class CacheOutageTolerantErrorHandlerTest {

    private final CacheOutageTolerantErrorHandler handler = new CacheOutageTolerantErrorHandler();
    private final Cache cache = mock(Cache.class, withSettings().stubOnly());
    private final Object key = new Object();
    private final Object value = new Object();

    @Test
    void getError_infrastructureOutage_degradesToMissWithoutThrowing() {
        assertDoesNotThrow(() -> handler.handleCacheGetError(
                new RedisConnectionFailureException("connection refused"), cache, key));
        assertDoesNotThrow(() -> handler.handleCacheGetError(
                new RedisSystemException("wrapped lettuce timeout", new RuntimeException()), cache, key));
        assertDoesNotThrow(() -> handler.handleCacheGetError(
                new QueryTimeoutException("timed out"), cache, key));
    }

    @Test
    void putAndEvictAndClearErrors_infrastructureOutage_swallowed() {
        assertDoesNotThrow(() -> handler.handleCachePutError(
                new RedisConnectionFailureException("down"), cache, key, value));
        assertDoesNotThrow(() -> handler.handleCacheEvictError(
                new RedisSystemException("down", new RuntimeException()), cache, key));
        assertDoesNotThrow(() -> handler.handleCacheClearError(
                new RedisConnectionFailureException("down"), cache));
    }

    @Test
    void getError_unexpectedException_stillDegradesWithoutThrowing() {
        // Policy: the cache layer never breaks the request path — even non-infrastructure
        // errors degrade to a miss (loud WARN with stack), never propagate.
        assertDoesNotThrow(() -> handler.handleCacheGetError(
                new IllegalStateException("unexpected cache bug"), cache, key));
    }
}
