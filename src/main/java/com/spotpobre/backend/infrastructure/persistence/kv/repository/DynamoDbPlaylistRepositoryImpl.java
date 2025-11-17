package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

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
}
