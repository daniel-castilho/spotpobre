package com.spotpobre.backend.application.playlist.port.in;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.song.model.SongId;

public interface RemoveSongFromPlaylistUseCase {

    Playlist removeSongFromPlaylist(final RemoveSongFromPlaylistCommand command);

    record RemoveSongFromPlaylistCommand(PlaylistId playlistId, SongId songId) {
    }
}
