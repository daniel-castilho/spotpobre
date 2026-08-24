package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.artist.port.in.RequireArtistAccessUseCase;
import com.spotpobre.backend.application.song.port.in.ConfirmSongUploadUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.common.ConflictException;
import com.spotpobre.backend.domain.common.IdempotencyInProgressException;
import com.spotpobre.backend.domain.common.IdempotencyLeaseLostException;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.common.UploadIntegrityException;
import com.spotpobre.backend.domain.song.model.CompletedUploadPart;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.model.SongUpload;
import com.spotpobre.backend.domain.song.model.SongUploadState;
import com.spotpobre.backend.domain.song.model.StorageObjectHead;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import com.spotpobre.backend.domain.song.port.SongUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfirmSongUploadServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T15:00:00Z");
    private static final Instant LEASE = NOW.plusSeconds(120);

    private SongStoragePort songStoragePort;
    private SongMetadataRepository songMetadataRepository;
    private SongUploadRepository songUploadRepository;
    private AlbumRepository albumRepository;
    private RequireArtistAccessUseCase requireArtistAccess;
    private ConfirmSongUploadService service;

    private final AlbumId albumId = new AlbumId(UUID.randomUUID());
    private final ArtistId artistId = new ArtistId(UUID.randomUUID());
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        songStoragePort = mock(SongStoragePort.class);
        songMetadataRepository = mock(SongMetadataRepository.class);
        songUploadRepository = mock(SongUploadRepository.class);
        albumRepository = mock(AlbumRepository.class);
        requireArtistAccess = mock(RequireArtistAccessUseCase.class);
        service = new ConfirmSongUploadService(songStoragePort, songMetadataRepository,
                songUploadRepository, albumRepository, requireArtistAccess,
                Clock.fixed(NOW, ZoneId.of("UTC")));

        when(albumRepository.findById(albumId)).thenReturn(Optional.of(
                Album.builder().id(albumId).name("Album").artistId(artistId).build()));
        when(songStoragePort.headObject(any())).thenReturn(
                new StorageObjectHead("audio/mpeg", 1_000_000L));
        when(songUploadRepository.acquireCompletingLease(any(), eq(LEASE), eq(NOW))).thenReturn(true);
        when(songUploadRepository.markCompletedAndCreateSongIfAbsent(any(), any(), eq(NOW)))
                .thenReturn(true);
    }

    private SongUpload stagedPending() {
        return SongUpload.start(new SongId(UUID.randomUUID()), "Track", albumId, artistId,
                actorId, "audio/mpeg", 1_000_000L, null, NOW.minusSeconds(600),
                NOW.plus(Duration.ofHours(24)));
    }

    private ConfirmSongUploadUseCase.ConfirmSongUploadCommand commandFor(final SongUpload upload) {
        return new ConfirmSongUploadUseCase.ConfirmSongUploadCommand(upload.getSongId(),
                upload.getAlbumId(), upload.getStagingKey(), null, List.of(), actorId, false);
    }

    @Test
    void confirmUpload_pendingUpload_completesVerifiesPromotesAndCreatesSongTransactionally() {
        SongUpload upload = stagedPending();
        when(songUploadRepository.findBySongId(upload.getSongId())).thenReturn(Optional.of(upload));

        Song song = service.confirmUpload(commandFor(upload));

        assertEquals(upload.getSongId(), song.getId());
        assertEquals(upload.getFinalKey(), song.getStorageId(),
                "the playable song must target the promoted final key");
        verify(songStoragePort).confirmUpload(any());
        verify(songStoragePort).headObject(upload.getStagingKey());
        verify(songStoragePort).promoteObject(upload.getStagingKey(), upload.getFinalKey());
        verify(songUploadRepository).markCompletedAndCreateSongIfAbsent(upload, song, NOW);
        verify(songMetadataRepository, never()).save(any());
    }

    @Test
    void confirmUpload_completedReplay_returnsSameSongWithoutTouchingStorage() {
        SongUpload completed = stagedPending();
        completed.markCompleting(LEASE);
        completed.markCompleted();
        when(songUploadRepository.findBySongId(completed.getSongId()))
                .thenReturn(Optional.of(completed));
        Song existing = Song.create(completed.getSongId(), "Track", albumId, completed.getFinalKey());
        when(songMetadataRepository.findById(completed.getSongId())).thenReturn(Optional.of(existing));

        Song result = service.confirmUpload(commandFor(completed));

        assertEquals(existing, result);
        verify(songStoragePort, never()).confirmUpload(any());
        verify(songStoragePort, never()).promoteObject(any(), any());
    }

    @Test
    void confirmUpload_liveForeignLease_throwsInProgressWithRetryAfter() {
        SongUpload upload = stagedPending();
        when(songUploadRepository.findBySongId(upload.getSongId())).thenReturn(Optional.of(upload));
        when(songUploadRepository.acquireCompletingLease(any(), any(), any())).thenReturn(false);
        when(songUploadRepository.findBySongId(upload.getSongId())).thenReturn(Optional.of(upload));

        IdempotencyInProgressException ex = assertThrows(IdempotencyInProgressException.class,
                () -> service.confirmUpload(commandFor(upload)));
        assertTrue(ex.getRetryAfterSeconds() >= 1);
    }

    @Test
    void confirmUpload_sizeMismatch_quarantinesWithoutCreatingSong() {
        SongUpload upload = stagedPending();
        when(songUploadRepository.findBySongId(upload.getSongId())).thenReturn(Optional.of(upload));
        when(songStoragePort.headObject(upload.getStagingKey()))
                .thenReturn(new StorageObjectHead("audio/mpeg", 42L));

        assertThrows(UploadIntegrityException.class, () -> service.confirmUpload(commandFor(upload)));

        verify(songStoragePort).deleteObject(upload.getStagingKey());
        verify(songStoragePort, never()).promoteObject(any(), any());
        verify(songUploadRepository).releaseCompletingLease(eq(upload.getSongId()), eq(LEASE), eq(NOW));
        verify(songUploadRepository).markAbortedFromPendingOrExpiredCompleting(upload.getSongId(),
                NOW);
        verify(songMetadataRepository, never()).save(any());
    }

    @Test
    void confirmUpload_contentTypeMismatch_quarantinesWithoutCreatingSong() {
        SongUpload upload = stagedPending();
        when(songUploadRepository.findBySongId(upload.getSongId())).thenReturn(Optional.of(upload));
        when(songStoragePort.headObject(upload.getStagingKey()))
                .thenReturn(new StorageObjectHead("video/mp4", 1_000_000L));

        assertThrows(UploadIntegrityException.class, () -> service.confirmUpload(commandFor(upload)));

        verify(songStoragePort, never()).promoteObject(any(), any());
        verify(songMetadataRepository, never()).save(any());
    }

    @Test
    void confirmUpload_clientKeyMismatch_rejectedBeforeAnyLeaseOrStorageWork() {
        SongUpload upload = stagedPending();
        when(songUploadRepository.findBySongId(upload.getSongId())).thenReturn(Optional.of(upload));

        var badCommand = new ConfirmSongUploadUseCase.ConfirmSongUploadCommand(upload.getSongId(),
                upload.getAlbumId(), "songs/some-other-key", null, List.of(), actorId, false);

        assertThrows(ConflictException.class, () -> service.confirmUpload(badCommand));

        verify(songUploadRepository, never()).acquireCompletingLease(any(), any(), any());
        verify(songStoragePort, never()).confirmUpload(any());
    }

    @Test
    void confirmUpload_unknownSong_notFound() {
        when(songUploadRepository.findBySongId(any())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.confirmUpload(
                new ConfirmSongUploadUseCase.ConfirmSongUploadCommand(new SongId(UUID.randomUUID()),
                        albumId, "pending/x", null, List.of(), actorId, false)));
    }

    @Test
    void confirmUpload_transactionReportsLostLease_throwsLeaseLostForClientRetry() {
        SongUpload upload = stagedPending();
        when(songUploadRepository.findBySongId(upload.getSongId())).thenReturn(Optional.of(upload));
        when(songUploadRepository.markCompletedAndCreateSongIfAbsent(any(), any(), any()))
                .thenReturn(false);

        assertThrows(IdempotencyLeaseLostException.class,
                () -> service.confirmUpload(commandFor(upload)));

        verify(songMetadataRepository, never()).save(any());
    }

    @Test
    void confirmUpload_duplicateOrUnorderedParts_rejectedBeforeLeaseWork() {
        SongUpload upload = SongUpload.start(new SongId(UUID.randomUUID()), "Track",
                albumId, artistId, actorId, "audio/mpeg", 1_000_000L, "mpu-1",
                NOW.minusSeconds(600), NOW.plus(java.time.Duration.ofHours(24)));
        when(songUploadRepository.findBySongId(upload.getSongId()))
                .thenReturn(Optional.of(upload));

        var duplicateParts = new ConfirmSongUploadUseCase.ConfirmSongUploadCommand(
                upload.getSongId(), upload.getAlbumId(), upload.getStagingKey(), "mpu-1",
                java.util.List.of(new CompletedUploadPart(2, "\"e2\""),
                        new CompletedUploadPart(2, "\"e1\"")),
                actorId, false);
        assertThrows(ConflictException.class, () -> service.confirmUpload(duplicateParts));

        // Blank ETags are rejected earlier, at the CompletedUploadPart value object boundary.
        assertThrows(IllegalArgumentException.class,
                () -> new CompletedUploadPart(1, "  "));

        verify(songUploadRepository, never()).acquireCompletingLease(any(), any(), any());
    }

    @Test
    void confirmUpload_wrongMultipartIdRejected() {
        SongUpload multipartUpload = SongUpload.start(new SongId(UUID.randomUUID()), "Track",
                albumId, artistId, actorId, "audio/mpeg", 1_000_000L, "bound-mpu",
                NOW.minusSeconds(600), NOW.plus(Duration.ofHours(24)));
        when(songUploadRepository.findBySongId(multipartUpload.getSongId()))
                .thenReturn(Optional.of(multipartUpload));

        var badParts = new ConfirmSongUploadUseCase.ConfirmSongUploadCommand(
                multipartUpload.getSongId(), multipartUpload.getAlbumId(),
                multipartUpload.getStagingKey(), "client-invented-mpu",
                List.of(new CompletedUploadPart(1, "\"etag\"")), actorId, false);

        assertThrows(ConflictException.class, () -> service.confirmUpload(badParts));
        verify(songUploadRepository, never()).acquireCompletingLease(any(), any(), any());
        assertEquals(SongUploadState.PENDING_UPLOAD, multipartUpload.getState());
    }
}
