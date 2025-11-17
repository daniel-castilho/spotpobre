package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.model.DynamoDbPage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class DynamoDbPlaylistRepositoryImpl implements DynamoDbPlaylistRepository {

    private final DynamoDbTable<PlaylistDocument> playlistTable;

    @Override
    public PlaylistDocument save(final PlaylistDocument playlistDocument) {
        playlistTable.putItem(playlistDocument);
        return playlistDocument;
    }

    @Override
    public Optional<PlaylistDocument> findById(final UUID id) {
        return Optional.ofNullable(playlistTable.getItem(Key.builder().partitionValue(id.toString()).build()));
    }

    @Override
    public DynamoDbPage<PlaylistDocument> findAll(final Pageable pageable, final String exclusiveStartKey) {
        ScanEnhancedRequest.Builder requestBuilder = ScanEnhancedRequest.builder()
                .limit(pageable.getPageSize());

        if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
            Map<String, AttributeValue> startKey = Map.of("id", AttributeValue.builder().s(exclusiveStartKey).build());
            requestBuilder.exclusiveStartKey(startKey);
        }

        Page<PlaylistDocument> page = playlistTable.scan(requestBuilder.build()).iterator().next();
        List<PlaylistDocument> documents = page.items();
        Map<String, AttributeValue> lastEvaluatedKey = page.lastEvaluatedKey();

        String nextToken = null;
        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            // For simplicity, assuming the key is a single string attribute 'id'
            nextToken = lastEvaluatedKey.get("id").s();
        }

        return new DynamoDbPage<>(documents, nextToken);
    }
}
