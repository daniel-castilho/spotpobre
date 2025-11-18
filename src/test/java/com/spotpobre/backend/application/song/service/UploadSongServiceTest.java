package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.UploadSongUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadSongServiceTest {

    @Mock
    private SongStoragePort songStoragePort;

    @Mock
    private SongMetadataRepository songMetadataRepository;

    @Mock
    private AlbumRepository albumRepository;

    @InjectMocks
    private UploadSongService uploadSongService;

    @Test
    void shouldUploadSongSuccessfully() {
        // Given
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        byte[] fileContent = "fake-mp3-content".getBytes();
        UploadSongUseCase.UploadSongCommand command = new UploadSongUseCase.UploadSongCommand(
                "New Song Title", albumId, fileContent, "audio/mpeg"
        );
        String expectedStorageId = "storage-key-12345";

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(mock(Album.class)));
        when(songStoragePort.saveSong(any(SongFile.class))).thenReturn(expectedStorageId);

        // When
        Song uploadedSong = uploadSongService.uploadSong(command);

        // Then
        assertNotNull(uploadedSong);
        assertEquals("New Song Title", uploadedSong.getTitle());
        assertEquals(albumId, uploadedSong.getAlbumId());
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
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        UploadSongUseCase.UploadSongCommand command = new UploadSongUseCase.UploadSongCommand(
                "Failing Song", albumId, new byte[]{}, "audio/mpeg"
        );

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(mock(Album.class)));
        when(songStoragePort.saveSong(any(SongFile.class))).thenThrow(new RuntimeException("S3 is down"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            uploadSongService.uploadSong(command);
        });

        verify(songMetadataRepository, never()).save(any());
    }
}
