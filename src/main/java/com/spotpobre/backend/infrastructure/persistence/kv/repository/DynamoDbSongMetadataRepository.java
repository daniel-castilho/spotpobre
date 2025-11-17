package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface DynamoDbSongMetadataRepository {
    SongDocument save(final SongDocument songDocument);
    Optional<SongDocument> findById(final UUID id);
    Page<SongDocument> searchByTitle(final String titleQuery, final Pageable pageable);
}
