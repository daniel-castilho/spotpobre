package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.infrastructure.config.properties.AwsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3HealthIndicatorTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final AwsProperties awsProperties = new AwsProperties(
            "us-east-1", null,
            new AwsProperties.S3Properties("http://localstack:4566", "spotpobre-songs"),
            new AwsProperties.DynamoDbProperties("http://localstack:4566"));
    private final S3HealthIndicator indicator = new S3HealthIndicator(s3Client, awsProperties);

    @Test
    void health_whenBucketReachable_shouldBeUp() {
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(null);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("bucket", "spotpobre-songs");
    }

    @Test
    void health_whenBucketMissingOrUnreachable_shouldBeDown() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(new RuntimeException("404"));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
