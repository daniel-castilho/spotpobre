package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.infrastructure.config.properties.AwsProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/**
 * Readiness dependency check for S3 (S6 health model).
 *
 * <p>Checks that the configured song bucket exists and is reachable via {@code headBucket}.
 * S3 is a critical dependency for the readiness probe: without it uploads and streaming cannot
 * work. Reports {@code DOWN} when the bucket is missing or unreachable.
 */
@Component
public class S3HealthIndicator implements HealthIndicator {

    private final S3Client s3Client;
    private final AwsProperties awsProperties;

    public S3HealthIndicator(S3Client s3Client, AwsProperties awsProperties) {
        this.s3Client = s3Client;
        this.awsProperties = awsProperties;
    }

    @Override
    public Health health() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(awsProperties.s3().bucketName())
                    .build());
            return Health.up().withDetail("bucket", awsProperties.s3().bucketName()).build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}