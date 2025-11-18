package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.domain.album.model.AlbumId; // Import AlbumId
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
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
class GetSongMetadataServiceTest {

    @Mock
    private SongMetadataRepository songMetadataRepository;

    @InjectMocks
    private GetSongMetadataService getSongMetadataService;

    @Test
    void shouldReturnSongMetadataSuccessfully() {
        // Given
        SongId songId = new SongId(UUID.randomUUID());
        // Corrected: Provide a valid AlbumId when creating the song
        Song expectedSong = Song.create("Test Song", new AlbumId(UUID.randomUUID()), "storage-id");

        when(songMetadataRepository.findById(songId)).thenReturn(Optional.of(expectedSong));

        // When
        Song foundSong = getSongMetadataService.getSongMetadata(songId);

        // Then
        assertNotNull(foundSong);
        assertEquals(expectedSong, foundSong);
    }

    @Test
    void shouldThrowExceptionWhenSongMetadataNotFound() {
        // Given
        SongId songId = new SongId(UUID.randomUUID());

        when(songMetadataRepository.findById(songId)).thenReturn(Optional.empty());

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            getSongMetadataService.getSongMetadata(songId);
        });

        assertEquals("Song not found", exception.getMessage());
    }
}
