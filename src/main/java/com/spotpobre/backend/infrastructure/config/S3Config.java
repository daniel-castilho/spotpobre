package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.infrastructure.config.properties.AwsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    private final AwsProperties awsProperties;
    private final AwsCredentialsProviderResolver credentialsProviderResolver;

    public S3Config(AwsProperties awsProperties, AwsCredentialsProviderResolver credentialsProviderResolver) {
        this.awsProperties = awsProperties;
        this.credentialsProviderResolver = credentialsProviderResolver;
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .credentialsProvider(credentialsProviderResolver.resolve())
                .region(Region.of(awsProperties.region()))
                .endpointOverride(URI.create(awsProperties.s3().endpoint()))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .credentialsProvider(credentialsProviderResolver.resolve())
                .region(Region.of(awsProperties.region()))
                .endpointOverride(URI.create(awsProperties.s3().endpoint()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
