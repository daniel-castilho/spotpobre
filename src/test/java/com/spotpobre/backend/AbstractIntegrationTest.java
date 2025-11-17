package com.spotpobre.backend;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class AbstractIntegrationTest {

    private static final DockerImageName localstackImage = DockerImageName.parse("localstack/localstack:3.2");

    @Container
    private static final LocalStackContainer localstack = new LocalStackContainer(localstackImage)
            .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.DYNAMODB);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // AWS Endpoints
        registry.add("aws.s3.endpoint", () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        registry.add("aws.dynamodb.endpoint", () -> localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB).toString());
        
        // AWS Credentials for LocalStack using Spring Cloud AWS specific properties
        registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
        
        // AWS Region
        registry.add("aws.region", localstack::getRegion);
    }
}
