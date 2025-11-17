package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.CreatePlaylistUseCase;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatePlaylistServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlaylistRepository playlistRepository;

    @InjectMocks
    private CreatePlaylistService createPlaylistService;

    @Test
    void shouldCreatePlaylistSuccessfully() {
        // Given
        // 1. Create the user first to get a stable ID
        User owner = User.createWithLocalPassword(new UserProfile("Owner", "owner@test.com", "BR"), "pass");
        UserId ownerId = owner.getId(); // Use the actual ID from the created user

        // 2. Create the command with the correct ownerId
        CreatePlaylistUseCase.CreatePlaylistCommand command = new CreatePlaylistUseCase.CreatePlaylistCommand("My New Playlist", ownerId);

        // 3. Mock the repository to return this specific user when searched by its ID
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));

        // When
        Playlist createdPlaylist = createPlaylistService.createPlaylist(command);

        // Then
        assertNotNull(createdPlaylist);
        assertEquals("My New Playlist", createdPlaylist.getName());
        // The assertion will now pass because the ownerId from the command matches the ID of the user who created the playlist
        assertEquals(ownerId, createdPlaylist.getOwnerId());
        
        // Verify that the save method was called on the repository
        verify(playlistRepository, times(1)).save(any(Playlist.class));
    }

    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        // Given
        UserId nonExistentOwnerId = UserId.generate();
        CreatePlaylistUseCase.CreatePlaylistCommand command = new CreatePlaylistUseCase.CreatePlaylistCommand("Playlist for Ghost", nonExistentOwnerId);

        when(userRepository.findById(nonExistentOwnerId)).thenReturn(Optional.empty());

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            createPlaylistService.createPlaylist(command);
        });

        assertEquals("User not found", exception.getMessage());
        
        // Verify that save was never called
        verify(playlistRepository, never()).save(any());
    }
}
