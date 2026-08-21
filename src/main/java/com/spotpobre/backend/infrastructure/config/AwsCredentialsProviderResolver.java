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
 * <p>Credential source model (ADR-0002):
 *
 * <ul>
 *   <li>{@code aws.credentials.source=static} — emulated AWS endpoints such as on-premises
 *       LocalStack: both static keys must be set (LocalStack has no IAM).</li>
 *   <li>{@code aws.credentials.source=workload-identity} — real AWS: no static keys; the
 *       {@link DefaultCredentialsProvider} chain resolves the task/instance role. This keeps
 *       long-lived access keys out of real-AWS production.</li>
 *   <li>Source unset (dev/local) — inferred from key presence, preserving zero-config dev.</li>
 * </ul>
 *
 * <p>The prod profile additionally enforces the coherence rules at startup via
 * {@link ProdConfigValidator}; this resolver fails fast on an unknown source in any profile.
 */
@Component
public class AwsCredentialsProviderResolver {

    private final AwsProperties awsProperties;

    public AwsCredentialsProviderResolver(AwsProperties awsProperties) {
        this.awsProperties = awsProperties;
    }

    public AwsCredentialsProvider resolve() {
        String source = credentialsSource();
        if (source != null) {
            return switch (source) {
                case "static" -> staticProvider();
                case "workload-identity" -> DefaultCredentialsProvider.create();
                default -> throw new IllegalStateException(
                        "Invalid aws.credentials.source: '" + source + "'. "
                                + "Allowed values are 'static' or 'workload-identity'.");
            };
        }
        // Dev/local fallback: infer from key presence.
        if (hasStaticCredentials()) {
            return staticProvider();
        }
        return DefaultCredentialsProvider.create();
    }

    private String credentialsSource() {
        AwsProperties.CredentialsProperties credentials = awsProperties.credentials();
        if (credentials == null || credentials.source() == null || credentials.source().isBlank()) {
            return null;
        }
        return credentials.source().trim().toLowerCase();
    }

    private AwsCredentialsProvider staticProvider() {
        AwsProperties.CredentialsProperties credentials = awsProperties.credentials();
        if (!hasStaticCredentials()) {
            throw new IllegalStateException(
                    "aws.credentials.source=static requires both access and secret keys "
                            + "(emulated AWS endpoints have no IAM).");
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                credentials.accessKey(),
                credentials.secretKey()
        ));
    }

    private boolean hasStaticCredentials() {
        AwsProperties.CredentialsProperties credentials = awsProperties.credentials();
        return credentials != null
                && credentials.accessKey() != null
                && !credentials.accessKey().isBlank()
                && credentials.secretKey() != null
                && !credentials.secretKey().isBlank();
    }
}
