package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetSongStreamUrlServiceTest {

    @Mock
    private SongMetadataRepository songMetadataRepository;

    @Mock
    private SongStoragePort songStoragePort;

    @InjectMocks
    private GetSongStreamUrlService getSongStreamUrlService;

    @Test
    void shouldReturnStreamingUrlSuccessfully() {
        // Given
        SongId songId = new SongId(UUID.randomUUID());
        // Corrected: Provide a valid ArtistId
        Song song = Song.create("Test Song", new ArtistId(UUID.randomUUID()), "storage-id");
        URI expectedUri = URI.create("https://example.com/stream");

        when(songMetadataRepository.findById(songId)).thenReturn(Optional.of(song));
        when(songStoragePort.getStreamingUrl(songId)).thenReturn(expectedUri);

        // When
        URI streamingUrl = getSongStreamUrlService.getSongStreamUrl(songId);

        // Then
        assertNotNull(streamingUrl);
        assertEquals(expectedUri, streamingUrl);
    }

    @Test
    void shouldThrowExceptionWhenSongNotFoundForStreaming() {
        // Given
        SongId songId = new SongId(UUID.randomUUID());

        when(songMetadataRepository.findById(songId)).thenReturn(Optional.empty());

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            getSongStreamUrlService.getSongStreamUrl(songId);
        });

        assertEquals("Song not found", exception.getMessage());
        verify(songStoragePort, never()).getStreamingUrl(any());
    }
}
