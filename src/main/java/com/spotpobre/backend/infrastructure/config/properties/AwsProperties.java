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
     * Static credentials are optional: dev/local testing uses them, production must use the
     * ECS task role (workload identity) instead — set only the region and leave these null.
     */
    public record CredentialsProperties(
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
