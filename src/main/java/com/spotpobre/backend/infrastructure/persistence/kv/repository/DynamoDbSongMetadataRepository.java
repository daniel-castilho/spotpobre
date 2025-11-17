package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument; // Import the new top-level SongDocument

import java.util.Optional;
import java.util.UUID;

public interface DynamoDbSongMetadataRepository {
    SongDocument save(final SongDocument songDocument);
    Optional<SongDocument> findById(final UUID id);
}
