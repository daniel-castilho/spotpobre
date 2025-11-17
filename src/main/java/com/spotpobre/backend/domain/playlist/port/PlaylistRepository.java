package com.spotpobre.backend.domain.playlist.port;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.infrastructure.persistence.kv.model.DynamoDbPage; // Import DynamoDbPage
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface PlaylistRepository {
    Optional<Playlist> findById(final PlaylistId id);
    void save(final Playlist playlist);
    DynamoDbPage<Playlist> findAll(final Pageable pageable, final String exclusiveStartKey); // Updated method
}
