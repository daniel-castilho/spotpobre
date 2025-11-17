package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.ArtistDocument;

import java.util.Optional;
import java.util.UUID;

public interface DynamoDbArtistRepository {
    ArtistDocument save(final ArtistDocument artistDocument);
    Optional<ArtistDocument> findById(final UUID id);
}
