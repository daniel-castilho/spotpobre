package com.spotpobre.backend.infrastructure.security.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Atomic token-bucket authority backed by Redis (spec section 8.2). The Lua script reads
 * Redis TIME so refill does not depend on application clocks, and performs the whole
 * refill-spend-persist cycle in one atomic step: N racing instances can never over-admit.
 *
 * <p>Bucket state is a small hash ({@code tokens, ts}) with a TTL of roughly two refill
 * periods so idle keys expire instead of accumulating.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisTokenBucketLimiter {

    /**
     * KEYS[1] = bucket key; ARGV: capacity, refillPerSecond, requestedTokens.
     * Returns {allowed(0|1), remainingWholeTokens, resetSeconds}.
     */
    private static final String TOKEN_BUCKET_LUA = """
            local t = redis.call('TIME')
            local now = tonumber(t[1]) + tonumber(t[2]) / 1000000
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refillPerSecond = tonumber(ARGV[2])
            local requested = tonumber(ARGV[3])

            local state = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(state[1])
            local ts = tonumber(state[2])
            if tokens == nil or ts == nil then
                tokens = capacity
                ts = now
            end

            local elapsed = math.max(0, now - ts)
            tokens = math.min(capacity, tokens + elapsed * refillPerSecond)

            local allowed = 0
            if tokens >= requested then
                tokens = tokens - requested
                allowed = 1
            end

            local resetSeconds = 0
            if allowed == 0 then
                resetSeconds = math.ceil((requested - tokens) / refillPerSecond)
                if resetSeconds < 1 then resetSeconds = 1 end
            end

            redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
            local ttl = math.max(60, math.ceil(capacity / refillPerSecond) * 2)
            redis.call('EXPIRE', key, ttl)
            return {allowed, math.floor(tokens), resetSeconds}
            """;

    private final StringRedisTemplate redisTemplate;

    private final DefaultRedisScript<List<Long>> script = buildScript();

    @SuppressWarnings("unchecked")
    private static DefaultRedisScript<List<Long>> buildScript() {
        DefaultRedisScript<List<Long>> s = new DefaultRedisScript<>();
        s.setScriptText(TOKEN_BUCKET_LUA);
        s.setResultType((Class<List<Long>>) (Class<?>) List.class);
        return s;
    }

    /**
     * Attempts to spend {@code requested} tokens from the bucket identified by {@code key}.
     *
     * @throws org.springframework.data.redis.RedisConnectionFailureException when Redis is
     *         unreachable — callers translate per-policy into fail-closed 503 or fail-open warn
     */
    public TokenBucketResult tryAcquire(final String key, final int capacity,
                                       final Duration fullRefillDuration, final int requested) {
        double refillPerSecond = capacity / Math.max(0.001d, fullRefillDuration.toMillis() / 1000.0d);
        List<Long> result = redisTemplate.execute(script, List.of(key),
                String.valueOf(capacity), String.valueOf(refillPerSecond), String.valueOf(requested));
        if (result == null || result.size() < 3) {
            throw new IllegalStateException("Token-bucket script returned an unexpected shape");
        }
        long allowed = result.get(0);
        long remaining = result.get(1);
        long resetSeconds = result.get(2);
        return allowed == 1
                ? TokenBucketResult.allowed(remaining)
                : TokenBucketResult.blocked(resetSeconds);
    }
}
