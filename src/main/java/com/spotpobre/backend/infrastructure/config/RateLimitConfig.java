package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.infrastructure.config.properties.RateLimitProperties;
import com.spotpobre.backend.infrastructure.security.ratelimit.RateLimitKeyEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the Redis token-bucket rate-limit authority (spec section 8).
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimitKeyEncoder rateLimitKeyEncoder(final RateLimitProperties properties) {
        return new RateLimitKeyEncoder(properties.keySecret());
    }
}
