package com.spotpobre.backend.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Externalised configuration for the Redis token-bucket rate-limit authority (spec section 8).
 *
 * <p>Policies mirror section 8.3's table; every value is externally configurable. Subject keys
 * are HMAC'd with {@code keySecret} (env RATE_LIMIT_KEY_SECRET) — never store raw e-mails/IPs
 * in Redis. Trusted proxies are CIDRs whose forwarded headers may be believed.</p>
 */
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("") String keySecret,
        // Register: IP-wide 20/hour; IP+e-mail 5/hour
        @DefaultValue("20") int registerIpCapacity,
        @DefaultValue("1h") Duration registerIpRefill,
        @DefaultValue("5") int registerEmailCapacity,
        @DefaultValue("1h") Duration registerEmailRefill,
        // Authenticate: IP-wide 100/15min; IP+e-mail 10/15min
        @DefaultValue("100") int authenticateIpCapacity,
        @DefaultValue("15m") Duration authenticateIpRefill,
        @DefaultValue("10") int authenticateEmailCapacity,
        @DefaultValue("15m") Duration authenticateEmailRefill,
        // Upload initiate: user 20/min; user+album 40/hour
        @DefaultValue("20") int uploadInitiateUserCapacity,
        @DefaultValue("1m") Duration uploadInitiateUserRefill,
        @DefaultValue("40") int uploadInitiateAlbumCapacity,
        @DefaultValue("1h") Duration uploadInitiateAlbumRefill,
        // Upload confirm: user 60/min; user+album 120/hour
        @DefaultValue("60") int uploadConfirmUserCapacity,
        @DefaultValue("1m") Duration uploadConfirmUserRefill,
        @DefaultValue("120") int uploadConfirmAlbumCapacity,
        @DefaultValue("1h") Duration uploadConfirmAlbumRefill,
        // Search: 120/min (user, fallback trusted IP)
        @DefaultValue("120") int searchCapacity,
        @DefaultValue("1m") Duration searchRefill,
        // Verification resend cooldown: 1 per 2 minutes per address
        @DefaultValue("1") int resendCooldownCapacity,
        @DefaultValue("2m") Duration resendCooldownWindow,
        @DefaultValue({
                "/api/v1/auth/register",
                "/api/v1/auth/authenticate"
        }) List<String> anonymousPoliciesPaths,
        @DefaultValue("/api/v1/albums/{albumId}/songs") String uploadInitiatePathTemplate,
        @DefaultValue("/api/v1/albums/{albumId}/songs/{songId}/confirm") String uploadConfirmPathTemplate,
        @DefaultValue("/api/v1/songs/search") String searchPath,
        // CIDRs whose X-Forwarded-For/Forwarded headers may be trusted; production must not
        // trust the world (validated by ProdConfigValidator).
        @DefaultValue({"127.0.0.0/8", "::1/128"}) List<String> trustedProxyCidrs,
        @DefaultValue("X-Forwarded-For") String clientIpHeader
) {
}
