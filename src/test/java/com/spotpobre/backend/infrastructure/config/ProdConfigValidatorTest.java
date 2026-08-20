package com.spotpobre.backend.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        when(environment.getProperty("aws.dynamodb.endpoint")).thenReturn("https://dynamodb.us-east-1.amazonaws.com");
        when(environment.getProperty("aws.s3.endpoint")).thenReturn("https://s3.us-east-1.amazonaws.com");
        when(environment.getProperty("aws.s3.bucket-name")).thenReturn("spotpobre-songs");
        when(environment.getProperty("spring.data.redis.host")).thenReturn("redis.internal");
    }

    @Test
    void prodProfile_missingRequiredProperty_shouldFailFast() {
        when(environment.getActiveProfiles()).thenReturn(prodProfile);
        when(environment.getProperty("jwt.secret")).thenReturn(null);

        ProdConfigValidator validator = new ProdConfigValidator(environment);

        assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
    }

    @Test
    void prodProfile_withStaticCredentials_shouldFailFast() {
        givenRequiredProperties();
        when(environment.getActiveProfiles()).thenReturn(prodProfile);
        when(environment.getProperty("aws.credentials.access-key")).thenReturn("AKIA1234567890");

        ProdConfigValidator validator = new ProdConfigValidator(environment);

        assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
    }

    @Test
    void prodProfile_allRequiredAndNoStaticCredentials_shouldPass() {
        givenRequiredProperties();
        when(environment.getActiveProfiles()).thenReturn(prodProfile);
        when(environment.getProperty("aws.credentials.access-key")).thenReturn("");
        when(environment.getProperty("aws.credentials.secret-key")).thenReturn("");

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