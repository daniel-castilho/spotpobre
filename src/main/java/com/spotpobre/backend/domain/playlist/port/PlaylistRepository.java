package com.spotpobre.backend.domain.playlist.port;

import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.user.model.UserId;

import java.util.Optional;

public interface PlaylistRepository {
    Optional<Playlist> findById(final PlaylistId id);

    /**
     * Atomically persists the playlist and advances the owner's playlist count in one
     * transaction, refusing to exceed {@code maxPlaylistsPerOwner} playlists per owner. Two
     * strictly concurrent creations can no longer both pass a pre-count: the storage-level
     * condition serializes them. Raises {@code ConflictException} when the limit would be
     * exceeded.
     */
    void createWithinOwnerLimit(final Playlist playlist, final int maxPlaylistsPerOwner);

    void update(final Playlist playlist);
    void deleteById(final PlaylistId id);
    PageResult<Playlist> findByOwnerId(final UserId ownerId, final PageRequest pageRequest, final String exclusiveStartKey);
}
