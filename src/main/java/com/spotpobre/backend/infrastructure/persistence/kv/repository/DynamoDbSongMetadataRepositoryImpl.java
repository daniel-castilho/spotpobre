package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
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
    public Page<SongDocument> searchByTitle(final String titleQuery, final Pageable pageable) {
        DynamoDbIndex<SongDocument> index = songTable.index("title-search-index");
        QueryConditional queryConditional = QueryConditional
                .sortBeginsWith(k -> k.partitionValue("SONG").sortValue(titleQuery.toLowerCase()));

        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(pageable.getPageSize())
                .build();

        List<SongDocument> results = index.query(queryRequest)
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());

        // Note: DynamoDB query pagination is more complex. This is a simplified implementation.
        return new PageImpl<>(results, pageable, results.size());
    }
}
