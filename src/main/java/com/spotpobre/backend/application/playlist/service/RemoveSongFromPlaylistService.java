package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.PlaylistOwnershipGuard;
import com.spotpobre.backend.application.playlist.port.in.RemoveSongFromPlaylistUseCase;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class RemoveSongFromPlaylistService implements RemoveSongFromPlaylistUseCase {

    private final PlaylistRepository playlistRepository;

    @Override
    @Transactional
    public Playlist removeSongFromPlaylist(final RemoveSongFromPlaylistCommand command) {
        Playlist playlist = playlistRepository.findById(command.playlistId())
                .orElseThrow(() -> new NotFoundException("Playlist not found"));

        PlaylistOwnershipGuard.requireOwner(playlist, command.currentUserId());

        // Desired-state semantics: already-absent is a successful no-op (no write, no version bump).
        if (!playlist.ensureSongAbsent(command.songId())) {
            return playlist;
        }

        playlistRepository.update(playlist);
        return playlist;
    }
}
