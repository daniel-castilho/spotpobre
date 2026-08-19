package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument;

import java.util.Optional;
import java.util.UUID;

public interface DynamoDbPlaylistRepository {
    boolean create(final PlaylistDocument playlistDocument);
    boolean update(final PlaylistDocument playlistDocument);
    Optional<PlaylistDocument> findById(final UUID id);
    void deleteById(final UUID id);
    long countByOwnerId(final UUID ownerId);
    PageResult<PlaylistDocument> findByOwnerId(final UUID ownerId, final PageRequest pageRequest, final String exclusiveStartKey);
}