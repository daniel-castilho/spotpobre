package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.model.DynamoDbPage;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface DynamoDbPlaylistRepository {
    PlaylistDocument save(final PlaylistDocument playlistDocument);
    Optional<PlaylistDocument> findById(final UUID id);
    void deleteById(final UUID id); // New method
    DynamoDbPage<PlaylistDocument> findByOwnerId(final UUID ownerId, final Pageable pageable, final String exclusiveStartKey);
}
