package com.spotpobre.backend.infrastructure.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Removed import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "aws")
public record AwsProperties(
        @NotBlank
        String region,
        @Valid
        CredentialsProperties credentials,
        @Valid
        S3Properties s3,
        @Valid
        DynamoDbProperties dynamodb
) {
    /**
     * Credential source model (ADR-0002). {@code source} selects how AWS credentials are
     * obtained: {@code static} for emulated endpoints such as on-premises LocalStack (both keys
     * required), {@code workload-identity} for real AWS (keys must be unset; the SDK default
     * provider chain resolves the task/instance role). Unset in dev — the resolver then infers
     * from key presence.
     */
    public record CredentialsProperties(
            String source,
            String accessKey,
            String secretKey
    ) {
    }

    public record S3Properties(
            @NotBlank
            String endpoint,
            @NotBlank
            String bucketName
    ) {
    }

    public record DynamoDbProperties(
            @NotBlank
            String endpoint
    ) {
    }
}
