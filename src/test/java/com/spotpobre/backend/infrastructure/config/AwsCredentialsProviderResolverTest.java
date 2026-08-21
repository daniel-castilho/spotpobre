package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.infrastructure.config.properties.AwsProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AwsCredentialsProviderResolverTest {

    private AwsProperties awsProperties(AwsProperties.CredentialsProperties credentials) {
        return new AwsProperties("us-east-1", credentials,
                new AwsProperties.S3Properties("http://localstack:4566", "spotpobre-songs"),
                new AwsProperties.DynamoDbProperties("http://localstack:4566"));
    }

    @Test
    void staticSource_withKeys_shouldReturnStaticProvider() {
        AwsProperties properties = awsProperties(
                new AwsProperties.CredentialsProperties("static", "test", "test"));

        AwsCredentialsProvider provider = new AwsCredentialsProviderResolver(properties).resolve();

        assertThat(provider).isInstanceOf(StaticCredentialsProvider.class);
        assertThat(provider.resolveCredentials().accessKeyId()).isEqualTo("test");
    }

    @Test
    void staticSource_withoutKeys_shouldFailFast() {
        AwsProperties properties = awsProperties(
                new AwsProperties.CredentialsProperties("static", "", ""));

        assertThatThrownBy(() -> new AwsCredentialsProviderResolver(properties).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("static");
    }

    @Test
    void workloadIdentitySource_shouldReturnDefaultProvider() {
        AwsProperties properties = awsProperties(
                new AwsProperties.CredentialsProperties("workload-identity", null, null));

        AwsCredentialsProvider provider = new AwsCredentialsProviderResolver(properties).resolve();

        assertThat(provider).isInstanceOf(DefaultCredentialsProvider.class);
    }

    @Test
    void unknownSource_shouldFailFast() {
        AwsProperties properties = awsProperties(
                new AwsProperties.CredentialsProperties("env-file", "a", "b"));

        assertThatThrownBy(() -> new AwsCredentialsProviderResolver(properties).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("env-file");
    }

    @Test
    void unsetSource_withKeys_shouldInferStaticProvider() {
        AwsProperties properties = awsProperties(
                new AwsProperties.CredentialsProperties(null, "dev-key", "dev-secret"));

        AwsCredentialsProvider provider = new AwsCredentialsProviderResolver(properties).resolve();

        assertThat(provider).isInstanceOf(StaticCredentialsProvider.class);
        assertThat(provider.resolveCredentials().accessKeyId()).isEqualTo("dev-key");
    }

    @Test
    void unsetSource_withoutKeys_shouldFallBackToDefaultProvider() {
        AwsProperties properties = awsProperties(
                new AwsProperties.CredentialsProperties(null, null, null));

        AwsCredentialsProvider provider = new AwsCredentialsProviderResolver(properties).resolve();

        assertThat(provider).isInstanceOf(DefaultCredentialsProvider.class);
    }
}
