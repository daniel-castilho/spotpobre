package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.AlbumDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DynamoDbAlbumRepository {
    void save(AlbumDocument albumDocument);
    Optional<AlbumDocument> findById(UUID albumId);
    List<AlbumDocument> findByArtistId(UUID artistId);
}
