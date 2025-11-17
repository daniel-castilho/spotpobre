package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.GetPlaylistsUseCase;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class GetPlaylistsService implements GetPlaylistsUseCase {

    private final PlaylistRepository playlistRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<Playlist> getPlaylists(final Pageable pageable) {
        return playlistRepository.findAll(pageable);
    }
}
