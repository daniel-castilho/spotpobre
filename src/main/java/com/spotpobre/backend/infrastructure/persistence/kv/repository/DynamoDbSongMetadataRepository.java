package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface DynamoDbSongMetadataRepository {
    SongDocument save(final SongDocument songDocument);
    Optional<SongDocument> findById(final UUID id);
    PageResult<SongDocument> findByAlbumId(final UUID albumId, final PageRequest pageRequest);
    Page<SongDocument> searchByTitle(final String titleQuery, final Pageable pageable);
}