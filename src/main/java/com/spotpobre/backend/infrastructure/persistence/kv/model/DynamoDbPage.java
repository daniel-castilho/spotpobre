package com.spotpobre.backend.infrastructure.persistence.kv.model;

import java.util.List;

public record DynamoDbPage<T>(
        List<T> content,
        String lastEvaluatedKey
) {
}
