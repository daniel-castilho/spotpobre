package com.spotpobre.backend.application.like.service;

import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaylistLikeStrategyTest {

    @Mock
    private PlaylistRepository playlistRepository;

    @InjectMocks
    private PlaylistLikeStrategy playlistLikeStrategy;

    @Test
    void supports_shouldReturnTrueForPlaylist() {
        assertTrue(playlistLikeStrategy.supports(EntityType.PLAYLIST));
    }

    @Test
    void supports_shouldReturnFalseForOtherTypes() {
        assertFalse(playlistLikeStrategy.supports(EntityType.SONG));
        assertFalse(playlistLikeStrategy.supports(EntityType.ARTIST));
    }

    @Test
    void validateEntityExists_shouldPassWhenPlaylistExists() {
        // Given
        PlaylistId playlistId = PlaylistId.generate();
        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(Playlist.builder().build()));

        // When & Then
        assertDoesNotThrow(() -> playlistLikeStrategy.validateEntityExists(playlistId.value().toString()));
    }

    @Test
    void validateEntityExists_shouldThrowWhenPlaylistNotFound() {
        // Given
        PlaylistId playlistId = PlaylistId.generate();
        when(playlistRepository.findById(playlistId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            playlistLikeStrategy.validateEntityExists(playlistId.value().toString());
        });

        assertEquals("Playlist not found: " + playlistId.value(), exception.getMessage());
    }
}