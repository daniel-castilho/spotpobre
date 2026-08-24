package com.spotpobre.backend.infrastructure.security.ratelimit;

import com.spotpobre.backend.domain.common.RateLimiterUnavailableException;
import com.spotpobre.backend.infrastructure.config.properties.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitServiceTest {

    private RedisTokenBucketLimiter limiter;
    private RateLimitService service;

    @BeforeEach
    void setUp() {
        limiter = mock(RedisTokenBucketLimiter.class);
        RateLimitKeyEncoder encoder = new RateLimitKeyEncoder("test-secret");
        RateLimitProperties properties = new RateLimitProperties(
                true, "test-secret",
                20, Duration.ofHours(1), 5, Duration.ofHours(1),
                100, Duration.ofMinutes(15), 10, Duration.ofMinutes(15),
                20, Duration.ofMinutes(1), 40, Duration.ofHours(1),
                60, Duration.ofMinutes(1), 120, Duration.ofHours(1),
                120, Duration.ofMinutes(1), 1, Duration.ofMinutes(2),
                List.of("/api/v1/auth/register", "/api/v1/auth/authenticate"),
                "/api/v1/albums/{albumId}/songs",
                "/api/v1/albums/{albumId}/songs/{songId}/confirm",
                "/api/v1/songs/search",
                List.of("127.0.0.0/8", "::1/128"),
                "X-Forwarded-For");
        service = new RateLimitService(limiter, encoder, properties);
    }

    @Test
    void checkRegister_allBucketsAdmit_evaluationAllowedWithMostRestrictiveHeaders() {
        when(limiter.tryAcquire(anyString(), anyInt(), any(Duration.class), anyInt()))
                .thenReturn(TokenBucketResult.allowed(3));

        RateLimitEvaluation evaluation = service.checkRegister("127.0.0.1", "a@b.com");

        assertTrue(evaluation.allowed());
        // Most restrictive = smallest remaining fraction: 3/20 (ip) beats 3/5 (email).
        assertEquals(20, evaluation.limit());
        assertEquals(3, evaluation.remaining());
    }

    @Test
    void checkRegister_emailBucketBlocked_evaluationBlockedWithRetryAfter() {
        when(limiter.tryAcquire(anyString(), anyInt(), any(Duration.class), anyInt()))
                .thenReturn(TokenBucketResult.allowed(10))
                .thenReturn(TokenBucketResult.blocked(60));

        RateLimitEvaluation evaluation = service.checkRegister("127.0.0.1", "a@b.com");

        assertFalse(evaluation.allowed());
        assertEquals(60, evaluation.retryAfterSeconds());
    }

    @Test
    void checkRegister_redisOutage_failsClosedWith503Carrier() {
        when(limiter.tryAcquire(anyString(), anyInt(), any(Duration.class), anyInt()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThrows(RateLimiterUnavailableException.class,
                () -> service.checkRegister("127.0.0.1", "a@b.com"),
                "register must fail closed on backend outage");
    }

    @Test
    void checkSearch_redisOutage_failsOpen() {
        when(limiter.tryAcquire(anyString(), anyInt(), any(Duration.class), anyInt()))
                .thenThrow(new RedisConnectionFailureException("down"));

        RateLimitEvaluation evaluation = service.checkSearch("user-key");

        assertTrue(evaluation.allowed(),
                "search fails open with warn + metric per policy table");
    }

    @Test
    void checkUploadInitiate_albumBucketDrivesTheOutcome() {
        when(limiter.tryAcquire(anyString(), anyInt(), any(Duration.class), anyInt()))
                .thenReturn(TokenBucketResult.allowed(19))
                .thenReturn(TokenBucketResult.blocked(300));

        RateLimitEvaluation evaluation = service.checkUploadInitiate("user", "album");

        assertFalse(evaluation.allowed());
        assertEquals(300, evaluation.retryAfterSeconds());
    }

    @Test
    void disabledAuthority_bucketsAdmitWithoutTouchingRedis() {
        RateLimitProperties disabled = new RateLimitProperties(
                false, "test-secret",
                20, Duration.ofHours(1), 5, Duration.ofHours(1),
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
        RateLimitService off = new RateLimitService(limiter, new RateLimitKeyEncoder("test-secret"), disabled);

        assertTrue(off.checkRegister("127.0.0.1", "a@b.com").allowed());
        assertTrue(off.checkResendCooldown("a@b.com").allowed());
        verify(limiter, never()).tryAcquire(anyString(), anyInt(), any(Duration.class), anyInt());
    }

    @Test
    void checkResendCooldown_delegatesToLimiter() {
        when(limiter.tryAcquire(anyString(), anyInt(), any(Duration.class), anyInt()))
                .thenReturn(TokenBucketResult.allowed(0));

        assertTrue(service.checkResendCooldown("a@b.com").allowed());
    }
}
