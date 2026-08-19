package com.spotpobre.backend.infrastructure.persistence.kv.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encodes/decodes the DynamoDB {@code LastEvaluatedKey} into an opaque, URL-safe token.
 *
 * <p>The key map is reduced to its scalar string values (all key attributes in this schema are
 * {@code S}-typed) and stored with a type prefix so a future numeric key stays unambiguous.
 */
@Component
public class DynamoDbCursorHelper {

    private static final String STRING_PREFIX = "S:";
    private static final String NUMBER_PREFIX = "N:";

    private final ObjectMapper objectMapper;

    public DynamoDbCursorHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encodeCursor(Map<String, AttributeValue> lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }
        Map<String, String> scalarKeys = new LinkedHashMap<>();
        lastEvaluatedKey.forEach((key, value) -> scalarKeys.put(key, attributeValueToScalar(value)));
        try {
            return Base64.getUrlEncoder().encodeToString(
                    objectMapper.writeValueAsString(scalarKeys).getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize pagination cursor", e);
        }
    }

    public Map<String, AttributeValue> decodeCursor(String encodedCursor) {
        if (encodedCursor == null || encodedCursor.isEmpty()) {
            return null;
        }
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedCursor);
            Map<String, String> scalarKeys = objectMapper.readValue(
                    decodedBytes, new TypeReference<Map<String, String>>() {});
            Map<String, AttributeValue> result = new LinkedHashMap<>();
            scalarKeys.forEach((key, value) -> result.put(key, scalarToAttributeValue(value)));
            return result;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Invalid or malformed pagination cursor", e);
        }
    }

    private static String attributeValueToScalar(AttributeValue value) {
        if (value.s() != null) {
            return STRING_PREFIX + value.s();
        }
        if (value.n() != null) {
            return NUMBER_PREFIX + value.n();
        }
        throw new IllegalArgumentException("Unsupported cursor attribute type");
    }

    private static AttributeValue scalarToAttributeValue(String scalar) {
        if (scalar.startsWith(STRING_PREFIX)) {
            return AttributeValue.builder().s(scalar.substring(STRING_PREFIX.length())).build();
        }
        if (scalar.startsWith(NUMBER_PREFIX)) {
            return AttributeValue.builder().n(scalar.substring(NUMBER_PREFIX.length())).build();
        }
        throw new IllegalArgumentException("Invalid or malformed pagination cursor");
    }
}