package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.PlaylistOwnershipGuard;
import com.spotpobre.backend.application.playlist.port.in.DeletePlaylistUseCase;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class DeletePlaylistService implements DeletePlaylistUseCase {

    private final PlaylistRepository playlistRepository;

    @Override
    @Transactional
    public void deletePlaylist(final DeletePlaylistCommand command) {
        Playlist playlist = playlistRepository.findById(command.playlistId())
                .orElseThrow(() -> new NotFoundException("Playlist not found"));

        PlaylistOwnershipGuard.requireOwner(playlist, command.currentUserId());

        playlistRepository.deleteById(command.playlistId());
    }
}
