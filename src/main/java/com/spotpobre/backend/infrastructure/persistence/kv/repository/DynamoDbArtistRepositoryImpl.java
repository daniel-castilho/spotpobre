package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.ArtistDocument;
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
public class DynamoDbArtistRepositoryImpl implements DynamoDbArtistRepository {

    private final DynamoDbTable<ArtistDocument> artistTable;

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
    public Page<ArtistDocument> searchByName(final String nameQuery, final Pageable pageable) {
        DynamoDbIndex<ArtistDocument> index = artistTable.index("name-search-index");
        QueryConditional queryConditional = QueryConditional
                .sortBeginsWith(k -> k.partitionValue("ARTIST").sortValue(nameQuery.toLowerCase()));

        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(pageable.getPageSize())
                .build();

        List<ArtistDocument> results = index.query(queryRequest)
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());

        return new PageImpl<>(results, pageable, results.size());
    }
}
