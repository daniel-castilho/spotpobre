package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.infrastructure.config.properties.AwsProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/**
 * Resolves the AWS credential provider from {@link AwsProperties}.
 *
 * <p>When static access keys are configured (dev/local, LocalStack) a
 * {@link StaticCredentialsProvider} is used. In production no static keys are set — the provider
 * falls back to the {@link DefaultCredentialsProvider}, which on ECS Fargate resolves the task
 * role (workload identity) from the container's instance metadata. This removes the need for any
 * long-lived {@code AWS_ACCESS_KEY_ID} / {@code AWS_SECRET_ACCESS_KEY} in production.
 */
@Component
public class AwsCredentialsProviderResolver {

    private final AwsProperties awsProperties;

    public AwsCredentialsProviderResolver(AwsProperties awsProperties) {
        this.awsProperties = awsProperties;
    }

    public AwsCredentialsProvider resolve() {
        if (hasStaticCredentials()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    awsProperties.credentials().accessKey(),
                    awsProperties.credentials().secretKey()
            ));
        }
        return DefaultCredentialsProvider.create();
    }

    private boolean hasStaticCredentials() {
        return awsProperties.credentials() != null
                && awsProperties.credentials().accessKey() != null
                && !awsProperties.credentials().accessKey().isBlank()
                && awsProperties.credentials().secretKey() != null
                && !awsProperties.credentials().secretKey().isBlank();
    }
}