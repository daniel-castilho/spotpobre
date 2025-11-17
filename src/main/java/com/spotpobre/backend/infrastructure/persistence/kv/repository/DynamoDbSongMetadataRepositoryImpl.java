package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument; // Import the new top-level SongDocument
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.util.Optional;
import java.util.UUID;

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
}
