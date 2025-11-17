package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.ArtistDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface DynamoDbArtistRepository {
    ArtistDocument save(final ArtistDocument artistDocument);
    Optional<ArtistDocument> findById(final UUID id);
    Page<ArtistDocument> searchByName(final String nameQuery, final Pageable pageable);
}
