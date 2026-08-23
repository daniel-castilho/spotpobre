package com.spotpobre.backend.infrastructure.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;

/**
 * Readiness dependency check for DynamoDB (S6 health model).
 *
 * <p>Performs a cheap operation (list at most one table) and reports {@code UP} on success,
 * {@code DOWN} on any failure. DynamoDB is a critical dependency for the readiness probe: without
 * it the service cannot serve any request.
 */
@Component
public class DynamoDbHealthIndicator implements HealthIndicator {

    private final DynamoDbClient dynamoDbClient;

    public DynamoDbHealthIndicator(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public Health health() {
        try {
            dynamoDbClient.listTables(ListTablesRequest.builder().limit(1).build()).tableNames();
            return Health.up().withDetail("location", "dynamodb").build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}