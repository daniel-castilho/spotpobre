package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.CreatePlaylistUseCase;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

public class CreatePlaylistService implements CreatePlaylistUseCase {

    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;

    public CreatePlaylistService(final UserRepository userRepository, final PlaylistRepository playlistRepository) {
        this.userRepository = userRepository;
        this.playlistRepository = playlistRepository;
    }

    @Override
    @Transactional
    public Playlist createPlaylist(final CreatePlaylistCommand command) {
        final User user = userRepository.findById(command.ownerId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (playlistRepository.countByOwnerId(command.ownerId()) >= User.MAX_PLAYLISTS_PER_USER) {
            throw new IllegalStateException("User cannot have more than " + User.MAX_PLAYLISTS_PER_USER + " playlists.");
        }

        final Playlist playlist = Playlist.create(command.name(), user.getId());

        playlistRepository.create(playlist);

        return playlist;
    }
}
