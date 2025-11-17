package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.UploadSongUseCase;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongFile;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadSongServiceTest {

    @Mock
    private SongStoragePort songStoragePort;

    @Mock
    private SongMetadataRepository songMetadataRepository;

    @InjectMocks
    private UploadSongService uploadSongService;

    @Test
    void shouldUploadSongSuccessfully() {
        // Given
        ArtistId artistId = new ArtistId(UUID.randomUUID());
        byte[] fileContent = "fake-mp3-content".getBytes();
        UploadSongUseCase.UploadSongCommand command = new UploadSongUseCase.UploadSongCommand(
                "New Song Title", artistId, fileContent, "audio/mpeg"
        );
        String expectedStorageId = "storage-key-12345";

        when(songStoragePort.saveSong(any(SongFile.class))).thenReturn(expectedStorageId);

        // When
        Song uploadedSong = uploadSongService.uploadSong(command);

        // Then
        assertNotNull(uploadedSong);
        assertEquals("New Song Title", uploadedSong.getTitle());
        assertEquals(artistId, uploadedSong.getArtistId());
        assertEquals(expectedStorageId, uploadedSong.getStorageId());

        ArgumentCaptor<SongFile> songFileCaptor = ArgumentCaptor.forClass(SongFile.class);
        verify(songStoragePort, times(1)).saveSong(songFileCaptor.capture());
        assertArrayEquals(fileContent, songFileCaptor.getValue().content());
        assertEquals("audio/mpeg", songFileCaptor.getValue().contentType());

        verify(songMetadataRepository, times(1)).save(uploadedSong);
    }

    @Test
    void shouldNotSaveMetadataWhenStorageFails() {
        // Given
        UploadSongUseCase.UploadSongCommand command = new UploadSongUseCase.UploadSongCommand(
                "Failing Song", new ArtistId(UUID.randomUUID()), new byte[]{}, "audio/mpeg"
        );

        when(songStoragePort.saveSong(any(SongFile.class))).thenThrow(new RuntimeException("S3 is down"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            uploadSongService.uploadSong(command);
        });

        // Verify that metadata repository's save method was never called
        verify(songMetadataRepository, never()).save(any());
    }
}
