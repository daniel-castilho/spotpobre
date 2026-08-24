package com.spotpobre.backend.infrastructure.security.ratelimit;

/**
 * Aggregate result of evaluating all buckets for one policy.
 *
 * @param allowed       true when every evaluated bucket admitted the request
 * @param limit         capacity of the most restrictive evaluated bucket (header value)
 * @param remaining     remaining tokens of that bucket
 * @param resetSeconds  refill seconds of that bucket
 * @param retryAfterSeconds on a block: seconds until the tightest blocked bucket refills
 */
public record RateLimitEvaluation(boolean allowed, int limit, long remaining,
                                  long resetSeconds, long retryAfterSeconds) {

    static RateLimitEvaluation allowed(final int limit, final long remaining,
                                       final long resetSeconds) {
        return new RateLimitEvaluation(true, limit, remaining, resetSeconds, 0);
    }

    static RateLimitEvaluation blocked(final int limit, final long retryAfterSeconds) {
        return new RateLimitEvaluation(false, limit, 0, retryAfterSeconds,
                Math.max(1, retryAfterSeconds));
    }
}
