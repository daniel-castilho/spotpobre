package com.spotpobre.backend.application.playlist.port.in;

import com.spotpobre.backend.domain.playlist.model.PlaylistId;

public interface DeletePlaylistUseCase {
    void deletePlaylist(final PlaylistId playlistId);
}
