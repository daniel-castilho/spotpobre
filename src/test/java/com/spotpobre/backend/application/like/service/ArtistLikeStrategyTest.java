package com.spotpobre.backend.application.like.service;

import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.like.model.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtistLikeStrategyTest {

    @Mock
    private ArtistRepository artistRepository;

    @InjectMocks
    private ArtistLikeStrategy artistLikeStrategy;

    @Test
    void supports_shouldReturnTrueForArtist() {
        assertTrue(artistLikeStrategy.supports(EntityType.ARTIST));
    }

    @Test
    void supports_shouldReturnFalseForOtherTypes() {
        assertFalse(artistLikeStrategy.supports(EntityType.SONG));
        assertFalse(artistLikeStrategy.supports(EntityType.PLAYLIST));
    }

    @Test
    void validateEntityExists_shouldPassWhenArtistExists() {
        // Given
        ArtistId artistId = ArtistId.generate();
        when(artistRepository.findById(artistId)).thenReturn(Optional.of(Artist.builder().build()));

        // When & Then
        assertDoesNotThrow(() -> artistLikeStrategy.validateEntityExists(artistId.value().toString()));
    }

    @Test
    void validateEntityExists_shouldThrowWhenArtistNotFound() {
        // Given
        ArtistId artistId = ArtistId.generate();
        when(artistRepository.findById(artistId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            artistLikeStrategy.validateEntityExists(artistId.value().toString());
        });

        assertEquals("Artist not found: " + artistId.value(), exception.getMessage());
    }
}