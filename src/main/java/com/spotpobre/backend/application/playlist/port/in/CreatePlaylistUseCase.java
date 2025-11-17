package com.spotpobre.backend.application.playlist.port.in;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.user.model.UserId;

public interface CreatePlaylistUseCase {

    Playlist createPlaylist(final CreatePlaylistCommand command);

    record CreatePlaylistCommand(String name, UserId ownerId) {
    }
}
