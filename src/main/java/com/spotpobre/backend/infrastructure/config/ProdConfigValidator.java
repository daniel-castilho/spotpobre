package com.spotpobre.backend.infrastructure.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.InitializingBean;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Twelve-Factor factor 3 — fail fast when required production configuration is missing or
 * incoherent.
 *
 * <p>With the {@code prod} profile active, {@code application-prod.yaml} binds every
 * environment-specific value from an env var (e.g. {@code JWT_SECRET}). An unresolved
 * {@code ${...}} placeholder would otherwise reach the application as a literal string, so this
 * component refuses to boot with a clear message listing the first missing variable. It is a
 * no-op in every other profile. Enforced to run before the AWS clients are built via
 * {@code @DependsOn} in {@link DynamoDbConfig}.
 *
 * <p>Credential source model (ADR-0002): {@code AWS_CREDENTIALS_SOURCE} selects how production
 * obtains AWS credentials.
 *
 * <ul>
 *   <li>{@code static} — on-premises LocalStack target: static (dummy) keys are REQUIRED,
 *       because emulated AWS has no IAM.</li>
 *   <li>{@code workload-identity} — real AWS target (ADR-0001 path): static keys are FORBIDDEN;
 *       the SDK default provider chain resolves the task/instance role.</li>
 * </ul>
 */
@Component
public class ProdConfigValidator implements InitializingBean {

    private static final List<String> REQUIRED_PROPERTIES = List.of(
            "jwt.secret",
            "aws.region",
            "aws.dynamodb.endpoint",
            "aws.s3.endpoint",
            "aws.s3.bucket-name",
            "spring.data.redis.host",
            "aws.credentials.source",
            "rate-limit.key-secret",
            "email.from-address",
            "email.ses-endpoint"
    );

    private static final Set<String> VALID_CREDENTIAL_SOURCES = Set.of("static", "workload-identity");

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

        validateCredentialSource();
    }

    private void validateCredentialSource() {
        String source = environment.getProperty("aws.credentials.source").trim().toLowerCase();
        if (!VALID_CREDENTIAL_SOURCES.contains(source)) {
            throw new IllegalStateException(
                    "Invalid 'aws.credentials.source': '" + source + "'. "
                            + "Allowed values are 'static' (on-premises LocalStack — dummy keys "
                            + "required) or 'workload-identity' (real AWS — task/instance role, "
                            + "no static keys). See docs/adr/0002-onprem-bare-metal-platform.md.");
        }

        boolean accessKeySet = isResolved("aws.credentials.access-key");
        boolean secretKeySet = isResolved("aws.credentials.secret-key");

        if ("static".equals(source) && (!accessKeySet || !secretKeySet)) {
            throw new IllegalStateException(
                    "AWS_CREDENTIALS_SOURCE=static requires AWS_ACCESS_KEY_ID and "
                            + "AWS_SECRET_ACCESS_KEY to be set (emulated AWS endpoints have no IAM; "
                            + "use the LocalStack dummy keys). See docs/adr/0002-onprem-bare-metal-platform.md.");
        }

        if ("workload-identity".equals(source) && (accessKeySet || secretKeySet)) {
            throw new IllegalStateException(
                    "Forbidden production configuration: AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY "
                            + "are set but AWS_CREDENTIALS_SOURCE=workload-identity. Real AWS "
                            + "production must use the task/instance role via "
                            + "DefaultCredentialsProvider; unset the static keys (see "
                            + "docs/adr/0001-production-platform.md).");
        }
    }

    private boolean isResolved(String key) {
        try {
            String value = environment.getProperty(key);
            return value != null && !value.isBlank() && !value.contains("${");
        } catch (IllegalArgumentException e) {
            // Environment.getProperty throws on unresolved ${...} placeholders.
            return false;
        }
    }

    private boolean isProdProfileActive() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}
