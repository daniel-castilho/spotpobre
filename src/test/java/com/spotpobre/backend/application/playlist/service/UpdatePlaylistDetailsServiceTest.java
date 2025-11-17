package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.UpdatePlaylistDetailsUseCase;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.user.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdatePlaylistDetailsServiceTest {

    @Mock
    private PlaylistRepository playlistRepository;

    @InjectMocks
    private UpdatePlaylistDetailsService updatePlaylistDetailsService;

    @Test
    void shouldUpdatePlaylistNameSuccessfully() {
        // Given
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        String newName = "My Updated Rock Playlist";
        UpdatePlaylistDetailsUseCase.UpdatePlaylistDetailsCommand command = new UpdatePlaylistDetailsUseCase.UpdatePlaylistDetailsCommand(playlistId, newName);

        Playlist existingPlaylist = Playlist.create("Old Name", UserId.generate());
        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(existingPlaylist));

        // When
        Playlist updatedPlaylist = updatePlaylistDetailsService.updatePlaylistDetails(command);

        // Then
        assertNotNull(updatedPlaylist);
        assertEquals(newName, updatedPlaylist.getName());
        verify(playlistRepository, times(1)).save(updatedPlaylist);
    }

    @Test
    void shouldThrowExceptionWhenPlaylistToUpdateNotFound() {
        // Given
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        UpdatePlaylistDetailsUseCase.UpdatePlaylistDetailsCommand command = new UpdatePlaylistDetailsUseCase.UpdatePlaylistDetailsCommand(playlistId, "New Name");

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.empty());

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            updatePlaylistDetailsService.updatePlaylistDetails(command);
        });

        assertEquals("Playlist not found", exception.getMessage());
        verify(playlistRepository, never()).save(any());
    }
}
