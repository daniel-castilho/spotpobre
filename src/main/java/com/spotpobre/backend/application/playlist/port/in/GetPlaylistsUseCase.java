package com.spotpobre.backend.application.playlist.port.in;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetPlaylistsUseCase {
    Page<Playlist> getPlaylists(final Pageable pageable);
}
