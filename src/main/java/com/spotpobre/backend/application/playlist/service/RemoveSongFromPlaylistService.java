package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.RemoveSongFromPlaylistUseCase;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class RemoveSongFromPlaylistService implements RemoveSongFromPlaylistUseCase {

    private final PlaylistRepository playlistRepository;

    @Override
    @Transactional
    public Playlist removeSongFromPlaylist(final RemoveSongFromPlaylistCommand command) {
        Playlist playlist = playlistRepository.findById(command.playlistId())
                .orElseThrow(() -> new IllegalStateException("Playlist not found"));

        // Here we would add authorization logic to ensure the current user owns the playlist

        playlist.removeSong(command.songId());
        playlistRepository.save(playlist);
        return playlist;
    }
}
