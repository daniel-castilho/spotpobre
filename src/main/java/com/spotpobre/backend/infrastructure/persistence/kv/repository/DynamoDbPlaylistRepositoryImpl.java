package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.model.DynamoDbCursorHelper;
import lombok.RequiredArgsConstructor;
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
    private final DynamoDbCursorHelper cursorHelper;

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
    public void deleteById(final UUID id) {
        playlistTable.deleteItem(Key.builder().partitionValue(id.toString()).build());
    }

    @Override
    public PageResult<PlaylistDocument> findByOwnerId(final UUID ownerId, final PageRequest pageRequest, final String exclusiveStartKey) {
        DynamoDbIndex<PlaylistDocument> index = playlistTable.index("ownerId-index");

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(ownerId.toString())))
                .limit(pageRequest.pageSize());

        if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
            requestBuilder.exclusiveStartKey(cursorHelper.decodeCursor(exclusiveStartKey));
        }

        Optional<Page<PlaylistDocument>> page = index.query(requestBuilder.build()).stream().findFirst();
        List<PlaylistDocument> documents = page.map(Page::items).orElse(List.of());
        String nextToken = page.map(p -> cursorHelper.encodeCursor(p.lastEvaluatedKey())).orElse(null);
        boolean hasNext = nextToken != null;

        return new PageResult<>(
                documents,
                documents.size(),
                1,
                pageRequest.pageNumber(),
                pageRequest.pageSize(),
                hasNext,
                pageRequest.pageNumber() > 0,
                nextToken
        );
    }
}
