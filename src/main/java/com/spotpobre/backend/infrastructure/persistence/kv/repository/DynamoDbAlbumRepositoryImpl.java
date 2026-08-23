package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.AlbumDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.model.DynamoDbCursorHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DynamoDbAlbumRepositoryImpl implements DynamoDbAlbumRepository {

    private final DynamoDbTable<AlbumDocument> albumTable;
    private final DynamoDbCursorHelper cursorHelper;

    @Override
    public void save(AlbumDocument albumDocument) {
        albumTable.putItem(albumDocument);
    }

    @Override
    public Optional<AlbumDocument> findById(UUID albumId) {
        return Optional.ofNullable(albumTable.getItem(Key.builder().partitionValue(albumId.toString()).build()));
    }

    @Override
    public PageResult<AlbumDocument> findByArtistId(UUID artistId, PageRequest pageRequest, String exclusiveStartKey) {
        DynamoDbIndex<AlbumDocument> index = albumTable.index("artistId-index");

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(artistId.toString())))
                .limit(pageRequest.pageSize());

        if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
            requestBuilder.exclusiveStartKey(cursorHelper.decodeCursor(exclusiveStartKey));
        }

        Optional<software.amazon.awssdk.enhanced.dynamodb.model.Page<AlbumDocument>> page =
                index.query(requestBuilder.build()).stream().findFirst();
        List<AlbumDocument> documents = page.map(software.amazon.awssdk.enhanced.dynamodb.model.Page::items).orElse(List.of());
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
