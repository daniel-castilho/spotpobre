package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.AddSongToPlaylistUseCase;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import lombok.RequiredArgsConstructor;
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
                .orElseThrow(() -> new IllegalStateException("Playlist not found"));

        final Song song = songMetadataRepository.findById(command.songId())
                .orElseThrow(() -> new IllegalStateException("Song not found"));

        playlist.addSong(song);

        playlistRepository.save(playlist);

        return playlist;
    }
}
