package com.spotpobre.backend.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdConfigValidatorTest {

    @Mock
    private Environment environment;

    private final String[] prodProfile = {"prod"};
    private final String[] devProfile = {"dev"};

    private void givenRequiredProperties() {
        when(environment.getProperty("jwt.secret")).thenReturn("secret");
        when(environment.getProperty("aws.region")).thenReturn("us-east-1");
        when(environment.getProperty("aws.dynamodb.endpoint")).thenReturn("http://localstack:4566");
        when(environment.getProperty("aws.s3.endpoint")).thenReturn("http://localstack:4566");
        when(environment.getProperty("aws.s3.bucket-name")).thenReturn("spotpobre-songs");
        when(environment.getProperty("spring.data.redis.host")).thenReturn("redis");
        when(environment.getProperty("aws.credentials.source")).thenReturn("static");
        lenient().when(environment.getProperty("rate-limit.key-secret")).thenReturn("prod-rate-limit-secret");
    }

    private void givenCredentialKeys(String accessKey, String secretKey) {
        when(environment.getProperty("aws.credentials.access-key")).thenReturn(accessKey);
        when(environment.getProperty("aws.credentials.secret-key")).thenReturn(secretKey);
    }

    @Test
    void prodProfile_missingRequiredProperty_shouldFailFast() {
        when(environment.getActiveProfiles()).thenReturn(prodProfile);
        when(environment.getProperty("jwt.secret")).thenReturn(null);

        ProdConfigValidator validator = new ProdConfigValidator(environment);

        assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
    }

    @Test
    void prodProfile_missingCredentialSource_shouldFailFast() {
        givenRequiredProperties();
        when(environment.getActiveProfiles()).thenReturn(prodProfile);
        when(environment.getProperty("aws.credentials.source")).thenReturn(null);

        ProdConfigValidator validator = new ProdConfigValidator(environment);

        assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
    }

    @Test
    void prodProfile_unknownCredentialSource_shouldFailFast() {
        givenRequiredProperties();
        when(environment.getActiveProfiles()).thenReturn(prodProfile);
        when(environment.getProperty("aws.credentials.source")).thenReturn("env-file");

        ProdConfigValidator validator = new ProdConfigValidator(environment);

        assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
    }

    @Test
    void prodProfile_staticSourceWithoutKeys_shouldFailFast() {
        givenRequiredProperties();
        when(environment.getActiveProfiles()).thenReturn(prodProfile);
        givenCredentialKeys("", "");

        ProdConfigValidator validator = new ProdConfigValidator(environment);

        assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
    }

    @Test
    void prodProfile_staticSourceWithDummyKeys_shouldPass() {
        givenRequiredProperties();
        when(environment.getActiveProfiles()).thenReturn(prodProfile);
        givenCredentialKeys("test", "test");

        ProdConfigValidator validator = new ProdConfigValidator(environment);

        assertDoesNotThrow(validator::afterPropertiesSet);
    }

    @Test
    void prodProfile_workloadIdentitySourceWithStaticKeys_shouldFailFast() {
        givenRequiredProperties();
        when(environment.getActiveProfiles()).thenReturn(prodProfile);
        when(environment.getProperty("aws.credentials.source")).thenReturn("workload-identity");
        givenCredentialKeys("AKIA1234567890", "shhh");

        ProdConfigValidator validator = new ProdConfigValidator(environment);

        assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
    }

    @Test
    void prodProfile_workloadIdentitySourceWithoutKeys_shouldPass() {
        givenRequiredProperties();
        when(environment.getActiveProfiles()).thenReturn(prodProfile);
        when(environment.getProperty("aws.credentials.source")).thenReturn("workload-identity");
        givenCredentialKeys("", "");

        ProdConfigValidator validator = new ProdConfigValidator(environment);

        assertDoesNotThrow(validator::afterPropertiesSet);
    }

    @Test
    void nonProdProfile_shouldAlwaysPass() {
        when(environment.getActiveProfiles()).thenReturn(devProfile);

        ProdConfigValidator validator = new ProdConfigValidator(environment);

        assertDoesNotThrow(validator::afterPropertiesSet);
    }
}
