package com.spotpobre.backend.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level URLs used inside emails (verification/reset links). In production this must
 * point at the public base URL users can reach.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl) {

    public AppProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
    }
}
