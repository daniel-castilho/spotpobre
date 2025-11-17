package com.spotpobre.backend.infrastructure.persistence.kv.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Map;

@Component
public class DynamoDbCursorHelper {

    private final ObjectMapper objectMapper;

    public DynamoDbCursorHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encodeCursor(Map<String, AttributeValue> lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }
        try {
            // Convert the map to a JSON string, then Base64 encode it
            return Base64.getUrlEncoder().encodeToString(objectMapper.writeValueAsString(lastEvaluatedKey).getBytes());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize pagination cursor", e);
        }
    }

    public Map<String, AttributeValue> decodeCursor(String encodedCursor) {
        if (encodedCursor == null || encodedCursor.isEmpty()) {
            return null;
        }
        try {
            // Base64 decode the string, then deserialize the JSON to a map
            byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedCursor);
            return objectMapper.readValue(decodedBytes, new TypeReference<Map<String, AttributeValue>>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid or malformed pagination cursor", e);
        }
    }
}
