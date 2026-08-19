package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.InitiateSongUploadUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.song.model.PresignedUploadPart;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongUploadCommand;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitiateSongUploadServiceTest {

    @Mock
    private SongStoragePort songStoragePort;

    @Mock
    private SongMetadataRepository songMetadataRepository;

    @Mock
    private AlbumRepository albumRepository;

    @InjectMocks
    private InitiateSongUploadService initiateSongUploadService;

    @Test
    void initiateUpload_validAlbum_persistsPlaceholderAndReturnsPresignedUrl() {
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        InitiateSongUploadUseCase.InitiateSongUploadCommand command =
                new InitiateSongUploadUseCase.InitiateSongUploadCommand(
                        "New Song Title", albumId, "audio/mpeg", 1024L
                );
        String expectedStorageId = "storage-key-12345";
        PresignedUploadResult upload = new PresignedUploadResult(
                expectedStorageId,
                null,
                Instant.now().plusSeconds(600),
                false,
                List.of(new PresignedUploadPart(1, "https://s3.example/put"))
        );

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(Album.builder().build()));
        when(songStoragePort.generateUploadUrl(any(SongUploadCommand.class))).thenReturn(upload);

        InitiateSongUploadUseCase.InitiateSongUploadResult result =
                initiateSongUploadService.initiateUpload(command);

        assertNotNull(result.song());
        assertEquals("New Song Title", result.song().getTitle());
        assertEquals(albumId, result.song().getAlbumId());
        assertEquals(expectedStorageId, result.song().getStorageId());
        assertEquals(upload, result.upload());

        ArgumentCaptor<SongUploadCommand> uploadCaptor = ArgumentCaptor.forClass(SongUploadCommand.class);
        verify(songStoragePort, times(1)).generateUploadUrl(uploadCaptor.capture());
        assertEquals("audio/mpeg", uploadCaptor.getValue().contentType());
        assertEquals(1024L, uploadCaptor.getValue().contentLengthBytes());

        ArgumentCaptor<Song> songCaptor = ArgumentCaptor.forClass(Song.class);
        verify(songMetadataRepository, times(1)).save(songCaptor.capture());
        assertEquals(expectedStorageId, songCaptor.getValue().getStorageId());
    }

    @Test
    void initiateUpload_missingAlbum_doesNotGenerateUrl() {
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        InitiateSongUploadUseCase.InitiateSongUploadCommand command =
                new InitiateSongUploadUseCase.InitiateSongUploadCommand(
                        "Missing Album Song", albumId, "audio/mpeg", 1024L
                );

        when(albumRepository.findById(albumId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                initiateSongUploadService.initiateUpload(command));

        assertEquals("Album not found: " + albumId, exception.getMessage());
        verify(songStoragePort, never()).generateUploadUrl(any());
        verify(songMetadataRepository, never()).save(any());
    }

    @Test
    void initiateUpload_unsupportedContentType_doesNotSaveMetadata() {
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        InitiateSongUploadUseCase.InitiateSongUploadCommand command =
                new InitiateSongUploadUseCase.InitiateSongUploadCommand(
                        "Bad Type", albumId, "video/mp4", 1024L
                );

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(Album.builder().build()));

        assertThrows(IllegalArgumentException.class, () -> initiateSongUploadService.initiateUpload(command));

        verify(songStoragePort, never()).generateUploadUrl(any());
        verify(songMetadataRepository, never()).save(any());
    }

    @Test
    void initiateUpload_storageFailure_doesNotSaveMetadata() {
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        InitiateSongUploadUseCase.InitiateSongUploadCommand command =
                new InitiateSongUploadUseCase.InitiateSongUploadCommand(
                        "Failing Song", albumId, "audio/mpeg", 1024L
                );

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(Album.builder().build()));
        when(songStoragePort.generateUploadUrl(any(SongUploadCommand.class)))
                .thenThrow(new RuntimeException("S3 is down"));

        assertThrows(RuntimeException.class, () -> initiateSongUploadService.initiateUpload(command));

        verify(songMetadataRepository, never()).save(any());
    }

    @Test
    void initiateUpload_metadataSaveFailure_multipartUploadIsAborted() {
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        InitiateSongUploadUseCase.InitiateSongUploadCommand command =
                new InitiateSongUploadUseCase.InitiateSongUploadCommand(
                        "Song", albumId, "audio/mpeg", 200L * 1024 * 1024
                );
        String storageKey = "storage-key-12345";
        String multipartUploadId = "upload-id-12345";
        PresignedUploadResult upload = new PresignedUploadResult(
                storageKey,
                multipartUploadId,
                Instant.now().plusSeconds(600),
                true,
                List.of(
                        new PresignedUploadPart(1, "https://s3.example/part1"),
                        new PresignedUploadPart(2, "https://s3.example/part2")
                )
        );

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(Album.builder().build()));
        when(songStoragePort.generateUploadUrl(any(SongUploadCommand.class))).thenReturn(upload);
        doThrow(new RuntimeException("DynamoDB is down"))
                .when(songMetadataRepository).save(any(Song.class));

        assertThrows(RuntimeException.class, () -> initiateSongUploadService.initiateUpload(command));

        verify(songStoragePort, times(1)).abortUpload(storageKey, multipartUploadId);
    }

    @Test
    void initiateUpload_metadataSaveFailure_singlePartUploadHasNothingToAbort() {
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        InitiateSongUploadUseCase.InitiateSongUploadCommand command =
                new InitiateSongUploadUseCase.InitiateSongUploadCommand(
                        "Song", albumId, "audio/mpeg", 1024L
                );
        String storageKey = "storage-key-12345";
        PresignedUploadResult upload = new PresignedUploadResult(
                storageKey,
                null,
                Instant.now().plusSeconds(600),
                false,
                List.of(new PresignedUploadPart(1, "https://s3.example/put"))
        );

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(Album.builder().build()));
        when(songStoragePort.generateUploadUrl(any(SongUploadCommand.class))).thenReturn(upload);
        doThrow(new RuntimeException("DynamoDB is down"))
                .when(songMetadataRepository).save(any(Song.class));

        assertThrows(RuntimeException.class, () -> initiateSongUploadService.initiateUpload(command));

        verify(songStoragePort, never()).abortUpload(any(), any());
    }
}
