package com.spotpobre.backend.application.playlist.service;

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
class DeletePlaylistServiceTest {

    @Mock
    private PlaylistRepository playlistRepository;

    @InjectMocks
    private DeletePlaylistService deletePlaylistService;

    @Test
    void shouldDeletePlaylistSuccessfully() {
        // Given
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        Playlist existingPlaylist = Playlist.create("My Playlist", UserId.generate());

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(existingPlaylist));
        doNothing().when(playlistRepository).deleteById(playlistId);

        // When
        deletePlaylistService.deletePlaylist(playlistId);

        // Then
        verify(playlistRepository, times(1)).findById(playlistId);
        verify(playlistRepository, times(1)).deleteById(playlistId);
    }

    @Test
    void shouldThrowExceptionWhenPlaylistToDeleteNotFound() {
        // Given
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.empty());

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            deletePlaylistService.deletePlaylist(playlistId);
        });

        assertEquals("Playlist not found", exception.getMessage());
        verify(playlistRepository, never()).deleteById(any());
    }
}
