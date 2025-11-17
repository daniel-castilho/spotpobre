package com.spotpobre.backend.domain.playlist.port;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;

import java.util.Optional;

public interface PlaylistRepository {
    Optional<Playlist> findById(final PlaylistId id);
    void save(final Playlist playlist);
}
