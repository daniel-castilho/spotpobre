package com.spotpobre.backend.infrastructure.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "aws")
public record AwsProperties(
        @NotBlank
        String region,
        @Valid
        S3Properties s3,
        @Valid
        DynamoDbProperties dynamodb
) {
    public record S3Properties(
            @NotBlank
            URI endpoint,
            @NotBlank
            String bucketName
    ) {
    }

    public record DynamoDbProperties(
            @NotBlank
            URI endpoint
    ) {
    }
}
