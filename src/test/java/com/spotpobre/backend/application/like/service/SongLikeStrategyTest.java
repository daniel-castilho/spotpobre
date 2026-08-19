package com.spotpobre.backend.application.like.service;

import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SongLikeStrategyTest {

    @Mock
    private SongMetadataRepository songMetadataRepository;

    @InjectMocks
    private SongLikeStrategy songLikeStrategy;

    @Test
    void supports_shouldReturnTrueForSong() {
        assertTrue(songLikeStrategy.supports(EntityType.SONG));
    }

    @Test
    void supports_shouldReturnFalseForOtherTypes() {
        assertFalse(songLikeStrategy.supports(EntityType.ARTIST));
        assertFalse(songLikeStrategy.supports(EntityType.PLAYLIST));
    }

    @Test
    void validateEntityExists_shouldPassWhenSongExists() {
        // Given
        SongId songId = SongId.generate();
        when(songMetadataRepository.findById(songId)).thenReturn(Optional.of(Song.builder().build()));

        // When & Then
        assertDoesNotThrow(() -> songLikeStrategy.validateEntityExists(songId.value().toString()));
    }

    @Test
    void validateEntityExists_shouldThrowWhenSongNotFound() {
        // Given
        SongId songId = SongId.generate();
        when(songMetadataRepository.findById(songId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            songLikeStrategy.validateEntityExists(songId.value().toString());
        });

        assertEquals("Song not found: " + songId.value(), exception.getMessage());
    }
}