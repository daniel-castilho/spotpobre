package com.spotpobre.backend.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Outbound email contract (Twelve-Factor factor 3): every environment-specific value binds from
 * an env var with a dev-safe default. {@code fromAddress} must be a verified SES identity before
 * production sends are accepted by AWS.
 *
 * @param verificationTtl lifetime of e-mail-verification tokens — deliberately separate from the
 *                        30-minute password-recovery TTL (binding decision v0.12.0).
 */
@ConfigurationProperties(prefix = "email")
public record EmailProperties(
        String fromAddress,
        String sesEndpoint,
        Duration verificationTtl) {

    public EmailProperties {
        if (fromAddress == null || fromAddress.isBlank()) {
            fromAddress = "no-reply@spotpobre.local";
        }
        if (sesEndpoint == null || sesEndpoint.isBlank()) {
            sesEndpoint = "http://localhost:4566";
        }
        if (verificationTtl == null || verificationTtl.isNegative() || verificationTtl.isZero()) {
            verificationTtl = Duration.ofHours(24);
        }
    }
}
