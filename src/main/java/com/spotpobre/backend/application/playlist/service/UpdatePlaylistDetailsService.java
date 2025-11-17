package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.UpdatePlaylistDetailsUseCase;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class UpdatePlaylistDetailsService implements UpdatePlaylistDetailsUseCase {

    private final PlaylistRepository playlistRepository;

    @Override
    @Transactional
    public Playlist updatePlaylistDetails(final UpdatePlaylistDetailsCommand command) {
        Playlist playlist = playlistRepository.findById(command.playlistId())
                .orElseThrow(() -> new IllegalStateException("Playlist not found"));

        // Here we would add authorization logic to ensure the current user owns the playlist

        playlist.updateDetails(command.newName());
        playlistRepository.save(playlist);
        return playlist;
    }
}
