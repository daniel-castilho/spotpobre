package com.spotpobre.backend.application.playlist.port.in;

import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.user.model.UserId;

public interface DeletePlaylistUseCase {

    void deletePlaylist(final DeletePlaylistCommand command);

    record DeletePlaylistCommand(PlaylistId playlistId, UserId currentUserId) {
    }
}
