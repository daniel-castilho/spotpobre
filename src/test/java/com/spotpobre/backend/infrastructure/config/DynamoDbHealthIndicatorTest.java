package com.spotpobre.backend.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamoDbHealthIndicatorTest {

    private final DynamoDbClient dynamoDbClient = mock(DynamoDbClient.class);
    private final DynamoDbHealthIndicator indicator = new DynamoDbHealthIndicator(dynamoDbClient);

    @Test
    void health_whenDynamoDbReachable_shouldBeUp() {
        when(dynamoDbClient.listTables(any(ListTablesRequest.class)))
                .thenReturn(ListTablesResponse.builder().tableNames(List.of("Users")).build());

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void health_whenDynamoDbUnavailable_shouldBeDown() {
        when(dynamoDbClient.listTables(any(ListTablesRequest.class)))
                .thenThrow(new RuntimeException("connection refused"));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
