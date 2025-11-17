package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.ArtistDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.util.Optional;
import java.util.UUID;

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
}
