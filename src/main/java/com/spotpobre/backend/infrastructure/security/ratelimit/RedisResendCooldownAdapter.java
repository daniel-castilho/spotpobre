package com.spotpobre.backend.infrastructure.security.ratelimit;

import com.spotpobre.backend.domain.user.port.VerificationResendCooldownPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Redis-backed adapter for the verification-resend cooldown: keys are HMAC'd so no raw
 * address is stored; failures surface as limiter-unavailable (fail closed, 503).
 */
@Component
@RequiredArgsConstructor
public class RedisResendCooldownAdapter implements VerificationResendCooldownPort {

    private final RateLimitService rateLimitService;

    @Override
    public boolean tryAcquire(final String subjectKey) {
        try {
            return rateLimitService.checkResendCooldown(subjectKey).allowed();
        } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
            throw new com.spotpobre.backend.domain.common.RateLimiterUnavailableException(
                    "Rate-limit backend temporarily unavailable");
        }
    }
}
