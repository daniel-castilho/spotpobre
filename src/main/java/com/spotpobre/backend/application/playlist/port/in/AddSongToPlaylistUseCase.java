package com.spotpobre.backend.application.playlist.port.in;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.user.model.UserId;

public interface AddSongToPlaylistUseCase {

    Playlist addSongToPlaylist(final AddSongToPlaylistCommand command);

    record AddSongToPlaylistCommand(PlaylistId playlistId, SongId songId, UserId currentUserId) {
    }
}
