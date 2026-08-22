package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.PlaylistOwnershipGuard;
import com.spotpobre.backend.application.playlist.port.in.AddSongToPlaylistUseCase;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistConcurrentModificationException;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import org.springframework.transaction.annotation.Transactional;

public class AddSongToPlaylistService implements AddSongToPlaylistUseCase {

    private final PlaylistRepository playlistRepository;
    private final SongMetadataRepository songMetadataRepository;

    public AddSongToPlaylistService(final PlaylistRepository playlistRepository, final SongMetadataRepository songMetadataRepository) {
        this.playlistRepository = playlistRepository;
        this.songMetadataRepository = songMetadataRepository;
    }

    @Override
    @Transactional
    public Playlist addSongToPlaylist(final AddSongToPlaylistCommand command) {
        final Playlist playlist = playlistRepository.findById(command.playlistId())
                .orElseThrow(() -> new NotFoundException("Playlist not found"));

        PlaylistOwnershipGuard.requireOwner(playlist, command.currentUserId());

        final Song song = songMetadataRepository.findById(command.songId())
                .orElseThrow(() -> new NotFoundException("Song not found"));

        // Desired-state semantics: already-present is a successful no-op (no write, no version bump).
        if (!playlist.ensureSongPresent(song)) {
            return playlist;
        }

        try {
            playlistRepository.update(playlist);
        } catch (PlaylistConcurrentModificationException e) {
            // Concurrent same-song PUT: reload; if the desired membership now exists the
            // operation has converged and must succeed rather than expose a conflict.
            final Playlist current = playlistRepository.findById(command.playlistId())
                    .orElseThrow(() -> new NotFoundException("Playlist not found"));
            if (current.containsSong(command.songId())) {
                return current;
            }
            throw e;
        }

        return playlist;
    }
}
