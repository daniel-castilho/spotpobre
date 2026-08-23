package com.spotpobre.backend.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbound email contract (Twelve-Factor factor 3): every environment-specific value binds from
 * an env var with a dev-safe default. {@code fromAddress} must be a verified SES identity before
 * production sends are accepted by AWS.
 */
@ConfigurationProperties(prefix = "email")
public record EmailProperties(
        String fromAddress,
        String sesEndpoint) {

    public EmailProperties {
        if (fromAddress == null || fromAddress.isBlank()) {
            fromAddress = "no-reply@spotpobre.local";
        }
        if (sesEndpoint == null || sesEndpoint.isBlank()) {
            sesEndpoint = "http://localhost:4566";
        }
    }
}
