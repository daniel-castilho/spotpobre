package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.CreatePlaylistUseCase;
import com.spotpobre.backend.domain.common.ConflictException;
import com.spotpobre.backend.domain.common.NotFoundException;
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
                .orElseThrow(() -> new NotFoundException("User not found"));

        final Playlist playlist = Playlist.create(command.name(), user.getId());

        // Atomic at the storage layer: the per-owner limit is enforced by the same transaction
        // that writes the playlist, so concurrent creations cannot both slip past it.
        playlistRepository.createWithinOwnerLimit(playlist, User.MAX_PLAYLISTS_PER_USER);

        return playlist;
    }
}
