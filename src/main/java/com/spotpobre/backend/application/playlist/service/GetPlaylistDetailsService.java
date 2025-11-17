package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.GetPlaylistDetailsUseCase;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

public class GetPlaylistDetailsService implements GetPlaylistDetailsUseCase {

    private final PlaylistRepository playlistRepository;

    public GetPlaylistDetailsService(final PlaylistRepository playlistRepository) {
        this.playlistRepository = playlistRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Playlist getPlaylistDetails(final PlaylistId playlistId) {
        return playlistRepository.findById(playlistId)
                .orElseThrow(() -> new IllegalStateException("Playlist not found"));
    }
}
