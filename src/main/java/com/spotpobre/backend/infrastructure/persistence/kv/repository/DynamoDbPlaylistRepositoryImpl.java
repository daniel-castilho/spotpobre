package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.model.DynamoDbPage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    public DynamoDbPage<PlaylistDocument> findByOwnerId(final UUID ownerId, final Pageable pageable, final String exclusiveStartKey) {
        DynamoDbIndex<PlaylistDocument> index = playlistTable.index("ownerId-index");

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(ownerId.toString())))
                .limit(pageable.getPageSize());

        if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
            Map<String, AttributeValue> startKey = Map.of(
                    "ownerId", AttributeValue.builder().s(ownerId.toString()).build(),
                    "id", AttributeValue.builder().s(exclusiveStartKey).build()
            );
            requestBuilder.exclusiveStartKey(startKey);
        }

        Page<PlaylistDocument> page = index.query(requestBuilder.build()).iterator().next();
        List<PlaylistDocument> documents = page.items();
        Map<String, AttributeValue> lastEvaluatedKey = page.lastEvaluatedKey();

        String nextToken = null;
        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            nextToken = lastEvaluatedKey.get("id").s();
        }

        return new DynamoDbPage<>(documents, nextToken);
    }
}
