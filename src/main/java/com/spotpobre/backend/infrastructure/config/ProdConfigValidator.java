package com.spotpobre.backend.infrastructure.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.InitializingBean;

import java.util.Arrays;
import java.util.List;

/**
 * Twelve-Factor factor 3 — fail fast when required production configuration is missing.
 *
 * <p>With the {@code prod} profile active, {@code application-prod.yaml} binds every
 * environment-specific value from an env var (e.g. {@code JWT_SECRET}). An unresolved
 * {@code ${...}} placeholder would otherwise reach the application as a literal string, so this
 * component refuses to boot with a clear message listing the first missing variable. It is a
 * no-op in every other profile. Enforced to run before the AWS clients are built via
 * {@code @DependsOn} in {@link DynamoDbConfig}.
 */
@Component
public class ProdConfigValidator implements InitializingBean {

    private static final List<String> REQUIRED_PROPERTIES = List.of(
            "jwt.secret",
            "aws.region",
            "aws.credentials.access-key",
            "aws.credentials.secret-key",
            "aws.dynamodb.endpoint",
            "aws.s3.endpoint",
            "aws.s3.bucket-name",
            "spring.data.redis.host"
    );

    private final Environment environment;

    public ProdConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (!isProdProfileActive()) {
            return;
        }

        for (String key : REQUIRED_PROPERTIES) {
            if (!isResolved(key)) {
                throw new IllegalStateException(
                        "Missing required production configuration: '" + key + "'. "
                                + "Set the corresponding environment variable before starting with "
                                + "the 'prod' profile (see application-prod.yaml for the contract).");
            }
        }
    }

    private boolean isResolved(String key) {
        try {
            String value = environment.getProperty(key);
            return value != null && !value.contains("${");
        } catch (IllegalArgumentException e) {
            // Environment.getProperty throws on unresolved ${...} placeholders.
            return false;
        }
    }

    private boolean isProdProfileActive() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}