package com.spotpobre.backend.domain.playlist.port;

import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.user.model.UserId;

import java.util.Optional;

public interface PlaylistRepository {
    Optional<Playlist> findById(final PlaylistId id);
    void create(final Playlist playlist);
    void update(final Playlist playlist);
    void deleteById(final PlaylistId id);
    long countByOwnerId(final UserId ownerId);
    PageResult<Playlist> findByOwnerId(final UserId ownerId, final PageRequest pageRequest, final String exclusiveStartKey);
}
