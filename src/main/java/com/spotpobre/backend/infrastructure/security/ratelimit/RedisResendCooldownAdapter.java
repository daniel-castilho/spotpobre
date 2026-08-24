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
        return rateLimitService.checkResendCooldown(subjectKey).allowed();
    }
}
