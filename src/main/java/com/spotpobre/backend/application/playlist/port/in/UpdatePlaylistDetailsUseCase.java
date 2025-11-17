package com.spotpobre.backend.application.playlist.port.in;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;

public interface UpdatePlaylistDetailsUseCase {

    Playlist updatePlaylistDetails(final UpdatePlaylistDetailsCommand command);

    record UpdatePlaylistDetailsCommand(PlaylistId playlistId, String newName) {
    }
}
