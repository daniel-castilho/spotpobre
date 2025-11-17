package com.spotpobre.backend.domain.playlist.port;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.infrastructure.persistence.kv.model.DynamoDbPage;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface PlaylistRepository {
    Optional<Playlist> findById(final PlaylistId id);
    void save(final Playlist playlist);
    void deleteById(final PlaylistId id); // New method
    DynamoDbPage<Playlist> findByOwnerId(final UserId ownerId, final Pageable pageable, final String exclusiveStartKey);
}
