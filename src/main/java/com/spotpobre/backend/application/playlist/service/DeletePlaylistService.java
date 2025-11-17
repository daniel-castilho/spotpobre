package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.DeletePlaylistUseCase;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class DeletePlaylistService implements DeletePlaylistUseCase {

    private final PlaylistRepository playlistRepository;

    @Override
    @Transactional
    public void deletePlaylist(final PlaylistId playlistId) {
        // First, check if the playlist exists
        playlistRepository.findById(playlistId)
                .orElseThrow(() -> new IllegalStateException("Playlist not found"));

        // Here we would add authorization logic to ensure the current user owns the playlist

        playlistRepository.deleteById(playlistId);
    }
}
