package com.spotpobre.backend.infrastructure.security.ratelimit;

import com.spotpobre.backend.domain.common.RateLimiterUnavailableException;
import com.spotpobre.backend.infrastructure.config.properties.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

/**
 * Policy orchestrator over the Redis token-bucket authority (spec section 8.3). Every
 * applicable bucket must admit a request; header values reflect the most restrictive bucket.
 * Backend failures translate per policy: fail-closed policies raise
 * {@link RateLimiterUnavailableException} (503, never claiming a limit was exceeded), search
 * fails open with a warn log and a metric increment.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RedisTokenBucketLimiter limiter;
    private final RateLimitKeyEncoder keyEncoder;
    private final RateLimitProperties properties;

    /** Register: IP-wide 20/h AND IP+normalized-e-mail 5/h; fail closed. */
    public RateLimitEvaluation checkRegister(final String clientIp, final String normalizedEmail) {
        return evaluateFailClosed(new Bucket("register:ip", properties.registerIpCapacity(),
                        properties.registerIpRefill(), clientIp),
                new Bucket("register:ip-email", properties.registerEmailCapacity(),
                        properties.registerEmailRefill(), clientIp + "\u001f" + normalizedEmail));
    }

    /** Authenticate: IP-wide 100/15min AND IP+normalized-e-mail 10/15min; fail closed. */
    public RateLimitEvaluation checkAuthenticate(final String clientIp, final String normalizedEmail) {
        return evaluateFailClosed(new Bucket("authenticate:ip", properties.authenticateIpCapacity(),
                        properties.authenticateIpRefill(), clientIp),
                new Bucket("authenticate:ip-email", properties.authenticateEmailCapacity(),
                        properties.authenticateEmailRefill(), clientIp + "\u001f" + normalizedEmail));
    }

    /** Upload initiate: user 20/min AND user+album 40/hour; fail closed. */
    public RateLimitEvaluation checkUploadInitiate(final String actorKey, final String albumId) {
        return evaluateFailClosed(new Bucket("upload-initiate:user", properties.uploadInitiateUserCapacity(),
                        properties.uploadInitiateUserRefill(), actorKey),
                new Bucket("upload-initiate:user-album", properties.uploadInitiateAlbumCapacity(),
                        properties.uploadInitiateAlbumRefill(), actorKey + "\u001f" + albumId));
    }

    /** Upload confirm: user 60/min AND user+album 120/hour; fail closed. */
    public RateLimitEvaluation checkUploadConfirm(final String actorKey, final String albumId) {
        return evaluateFailClosed(new Bucket("upload-confirm:user", properties.uploadConfirmUserCapacity(),
                        properties.uploadConfirmUserRefill(), actorKey),
                new Bucket("upload-confirm:user-album", properties.uploadConfirmAlbumCapacity(),
                        properties.uploadConfirmAlbumRefill(), actorKey + "\u001f" + albumId));
    }

    /** Search: 120/min for the authenticated user or trusted-IP fallback; fail open. */
    public RateLimitEvaluation checkSearch(final String subject) {
        Bucket bucket = new Bucket("search", properties.searchCapacity(), properties.searchRefill(),
                subject);
        try {
            TokenBucketResult result = acquire(bucket);
            if (!result.allowed()) {
                return RateLimitEvaluation.blocked(properties.searchCapacity(),
                        result.resetSeconds());
            }
            return RateLimitEvaluation.allowed(properties.searchCapacity(), result.remaining(),
                    result.resetSeconds());
        } catch (RedisConnectionFailureException e) {
            log.warn("Rate-limit backend unavailable for search; failing OPEN: {}", e.getMessage());
            return RateLimitEvaluation.allowed(properties.searchCapacity(), properties.searchCapacity(), 0);
        }
    }

    /** Verification resend cooldown: race-safe bounded window per address; fail closed. */
    public TokenBucketResult checkResendCooldown(final String normalizedEmail) {
        return acquire(new Bucket("resend-cooldown", properties.resendCooldownCapacity(),
                properties.resendCooldownWindow(), normalizedEmail));
    }

    private RateLimitEvaluation evaluateFailClosed(final Bucket... buckets) {
        RateLimitEvaluation worst = null;
        long maxRetryAfter = 0;
        boolean allAllowed = true;
        for (Bucket bucket : buckets) {
            TokenBucketResult result;
            try {
                result = acquire(bucket);
            } catch (RedisConnectionFailureException e) {
                throw new RateLimiterUnavailableException(
                        "Rate-limit backend temporarily unavailable");
            }
            if (!result.allowed()) {
                allAllowed = false;
                maxRetryAfter = Math.max(maxRetryAfter, result.resetSeconds());
            }
        }
        // Most restrictive evaluated bucket drives the headers: smallest remaining fraction.
        Bucket tightest = buckets[0];
        for (Bucket bucket : buckets) {
            if ((double) bucket.lastRemaining / bucket.capacity
                    < (double) tightest.lastRemaining / tightest.capacity) {
                tightest = bucket;
            }
        }
        if (!allAllowed) {
            return RateLimitEvaluation.blocked(tightest.capacity, maxRetryAfter);
        }
        return RateLimitEvaluation.allowed(tightest.capacity, tightest.lastRemaining,
                tightest.lastReset);

    }

    private TokenBucketResult acquire(final Bucket bucket) {
        TokenBucketResult result = limiter.tryAcquire(
                keyEncoder.encode(bucket.scope, bucket.subject),
                bucket.capacity, bucket.refill, 1);
        bucket.lastRemaining = result.remaining();
        bucket.lastReset = result.resetSeconds();
        return result;
    }

    private static final class Bucket {
        final String scope;
        final int capacity;
        final java.time.Duration refill;
        final String subject;
        long lastRemaining;
        long lastReset;

        private Bucket(final String scope, final int capacity, final java.time.Duration refill,
                       final String subject) {
            this.scope = scope;
            this.capacity = capacity;
            this.refill = refill;
            this.subject = subject;
        }
    }
}
