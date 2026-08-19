package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.model.DynamoDbCursorHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class DynamoDbSongMetadataRepositoryImpl implements DynamoDbSongMetadataRepository {

    private final DynamoDbTable<SongDocument> songTable;
    private final DynamoDbCursorHelper cursorHelper;

    @Override
    public SongDocument save(final SongDocument songDocument) {
        songTable.putItem(songDocument);
        return songDocument;
    }

    @Override
    public Optional<SongDocument> findById(final UUID id) {
        return Optional.ofNullable(songTable.getItem(Key.builder().partitionValue(id.toString()).build()));
    }

    @Override
    public PageResult<SongDocument> findByAlbumId(final UUID albumId, final PageRequest pageRequest) {
        DynamoDbIndex<SongDocument> index = songTable.index("albumId-index");
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(albumId.toString())))
                .limit(pageRequest.pageSize())
                .build();

        List<SongDocument> documents = index.query(queryRequest).stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());

        return new PageResult<>(
                documents,
                documents.size(),
                1,
                pageRequest.pageNumber(),
                pageRequest.pageSize(),
                false,
                pageRequest.pageNumber() > 0,
                null
        );
    }

    @Override
    public PageResult<SongDocument> searchByTitle(final String titleQuery, final PageRequest pageRequest, final String exclusiveStartKey) {
        DynamoDbIndex<SongDocument> index = songTable.index("title-search-index");
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional
                        .sortBeginsWith(k -> k.partitionValue("SONG").sortValue(titleQuery.toLowerCase())))
                .limit(pageRequest.pageSize());

        if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
            requestBuilder.exclusiveStartKey(cursorHelper.decodeCursor(exclusiveStartKey));
        }

        Optional<Page<SongDocument>> page = index.query(requestBuilder.build()).stream().findFirst();
        List<SongDocument> documents = page.map(Page::items).orElse(List.of());
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