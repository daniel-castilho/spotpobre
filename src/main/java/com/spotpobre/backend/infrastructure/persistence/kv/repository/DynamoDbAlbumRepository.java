package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.AlbumDocument;

import java.util.Optional;
import java.util.UUID;

public interface DynamoDbAlbumRepository {
    void save(AlbumDocument albumDocument);
    Optional<AlbumDocument> findById(UUID albumId);
    PageResult<AlbumDocument> findByArtistId(UUID artistId, PageRequest pageRequest, String exclusiveStartKey);
}
