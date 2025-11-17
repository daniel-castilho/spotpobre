package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument;

import java.util.Optional;
import java.util.UUID;

public interface DynamoDbPlaylistRepository {
    PlaylistDocument save(final PlaylistDocument playlistDocument);
    Optional<PlaylistDocument> findById(final UUID id);
}
