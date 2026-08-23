package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.infrastructure.config.properties.AwsProperties;
import com.spotpobre.backend.infrastructure.config.properties.EmailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

import java.net.URI;

@Configuration
public class SesConfig {

    private final AwsProperties awsProperties;
    private final EmailProperties emailProperties;
    private final AwsCredentialsProviderResolver credentialsProviderResolver;

    public SesConfig(AwsProperties awsProperties, EmailProperties emailProperties,
                     AwsCredentialsProviderResolver credentialsProviderResolver) {
        this.awsProperties = awsProperties;
        this.emailProperties = emailProperties;
        this.credentialsProviderResolver = credentialsProviderResolver;
    }

    @Bean
    public SesClient sesClient() {
        return SesClient.builder()
                .credentialsProvider(credentialsProviderResolver.resolve())
                .region(Region.of(awsProperties.region()))
                .endpointOverride(URI.create(emailProperties.sesEndpoint()))
                .build();
    }
}
