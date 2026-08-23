package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.ArtistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.model.DynamoDbCursorHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DynamoDbArtistRepositoryImpl implements DynamoDbArtistRepository {

    private final DynamoDbTable<ArtistDocument> artistTable;
    private final DynamoDbCursorHelper cursorHelper;

    @Override
    public ArtistDocument save(final ArtistDocument artistDocument) {
        artistTable.putItem(artistDocument);
        return artistDocument;
    }

    @Override
    public Optional<ArtistDocument> findById(final UUID id) {
        return Optional.ofNullable(artistTable.getItem(Key.builder().partitionValue(id.toString()).build()));
    }

    @Override
    public PageResult<ArtistDocument> searchByName(final String nameQuery, final PageRequest pageRequest, final String exclusiveStartKey) {
        DynamoDbIndex<ArtistDocument> index = artistTable.index("name-search-index");
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional
                        .sortBeginsWith(k -> k.partitionValue("ARTIST").sortValue(nameQuery.toLowerCase())))
                .limit(pageRequest.pageSize());

        if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
            requestBuilder.exclusiveStartKey(cursorHelper.decodeCursor(exclusiveStartKey));
        }

        Optional<Page<ArtistDocument>> page = index.query(requestBuilder.build()).stream().findFirst();
        List<ArtistDocument> documents = page.map(Page::items).orElse(List.of());
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

    @Override
    public PageResult<ArtistDocument> findAll(final PageRequest pageRequest, final String exclusiveStartKey) {
        ScanEnhancedRequest.Builder requestBuilder = ScanEnhancedRequest.builder()
                .limit(pageRequest.pageSize());

        if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
            requestBuilder.exclusiveStartKey(cursorHelper.decodeCursor(exclusiveStartKey));
        }

        Optional<Page<ArtistDocument>> page = artistTable.scan(requestBuilder.build()).stream().findFirst();
        List<ArtistDocument> documents = page.map(Page::items).orElse(List.of());
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