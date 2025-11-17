package com.spotpobre.backend.application.playlist.port.in;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;

public interface GetPlaylistDetailsUseCase {

    Playlist getPlaylistDetails(final PlaylistId playlistId);
}
