package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.ConfirmSongUploadUseCase;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.song.model.ConfirmUploadCommand;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmSongUploadServiceTest {

    @Mock
    private SongStoragePort songStoragePort;

    @Mock
    private SongMetadataRepository songMetadataRepository;

    @InjectMocks
    private ConfirmSongUploadService confirmSongUploadService;

    @Test
    void confirmUpload_matchingSong_delegatesToStoragePort() {
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        Song song = Song.create("Title", albumId, "storage-key");
        ConfirmSongUploadUseCase.ConfirmSongUploadCommand command =
                new ConfirmSongUploadUseCase.ConfirmSongUploadCommand(
                        song.getId(), albumId, "storage-key", null, List.of()
                );

        when(songMetadataRepository.findById(song.getId())).thenReturn(Optional.of(song));

        Song confirmed = confirmSongUploadService.confirmUpload(command);

        assertEquals(song, confirmed);
        ArgumentCaptor<ConfirmUploadCommand> captor = ArgumentCaptor.forClass(ConfirmUploadCommand.class);
        verify(songStoragePort).confirmUpload(captor.capture());
        assertEquals("storage-key", captor.getValue().storageKey());
    }

    @Test
    void confirmUpload_wrongAlbum_doesNotConfirmStorage() {
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        Song song = Song.create("Title", albumId, "storage-key");
        ConfirmSongUploadUseCase.ConfirmSongUploadCommand command =
                new ConfirmSongUploadUseCase.ConfirmSongUploadCommand(
                        song.getId(), new AlbumId(UUID.randomUUID()), "storage-key", null, List.of()
                );

        when(songMetadataRepository.findById(song.getId())).thenReturn(Optional.of(song));

        assertThrows(NotFoundException.class, () -> confirmSongUploadService.confirmUpload(command));
        verify(songStoragePort, never()).confirmUpload(any());
    }

    @Test
    void confirmUpload_wrongStorageKey_doesNotConfirmStorage() {
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        Song song = Song.create("Title", albumId, "storage-key");
        ConfirmSongUploadUseCase.ConfirmSongUploadCommand command =
                new ConfirmSongUploadUseCase.ConfirmSongUploadCommand(
                        song.getId(), albumId, "other-key", null, List.of()
                );

        when(songMetadataRepository.findById(song.getId())).thenReturn(Optional.of(song));

        assertThrows(NotFoundException.class, () -> confirmSongUploadService.confirmUpload(command));
        verify(songStoragePort, never()).confirmUpload(any());
    }

    @Test
    void confirmUpload_missingSong_doesNotConfirmStorage() {
        SongId songId = new SongId(UUID.randomUUID());
        ConfirmSongUploadUseCase.ConfirmSongUploadCommand command =
                new ConfirmSongUploadUseCase.ConfirmSongUploadCommand(
                        songId, new AlbumId(UUID.randomUUID()), "storage-key", null, List.of()
                );

        when(songMetadataRepository.findById(songId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> confirmSongUploadService.confirmUpload(command));
        verify(songStoragePort, never()).confirmUpload(any());
    }
}
