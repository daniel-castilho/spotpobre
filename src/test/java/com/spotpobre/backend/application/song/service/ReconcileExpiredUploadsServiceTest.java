package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.model.SongUpload;
import com.spotpobre.backend.domain.song.model.SongUploadState;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import com.spotpobre.backend.domain.song.port.SongUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconcileExpiredUploadsServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T18:00:00Z");

    private SongUploadRepository songUploadRepository;
    private SongStoragePort songStoragePort;
    private ReconcileExpiredUploadsService service;

    @BeforeEach
    void setUp() {
        songUploadRepository = mock(SongUploadRepository.class);
        songStoragePort = mock(SongStoragePort.class);
        service = new ReconcileExpiredUploadsService(songUploadRepository, songStoragePort,
                Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    private SongUpload expiredPending(final String multipartId) {
        return SongUpload.start(new SongId(UUID.randomUUID()), "Track", new AlbumId(UUID.randomUUID()),
                new ArtistId(UUID.randomUUID()), UUID.randomUUID(), "audio/mpeg", 1024L,
                multipartId, NOW.minusSeconds(7200), NOW.minusSeconds(1));
    }

    @Test
    void reconcileExpiredUploads_abortsMultipartDeletesStagingAndMarksAborted() {
        SongUpload pending = expiredPending("mpu-1");
        when(songUploadRepository.findExpiredByState(SongUploadState.PENDING_UPLOAD, NOW, 50))
                .thenReturn(List.of(pending));
        when(songUploadRepository.findExpiredByState(SongUploadState.COMPLETING, NOW, 50))
                .thenReturn(List.of());
        when(songUploadRepository.markAbortedFromPendingOrExpiredCompleting(pending.getSongId(), NOW))
                .thenReturn(true);

        var summary = service.reconcileExpiredUploads();

        assertEquals(1, summary.pendingAborted());
        assertEquals(0, summary.completingAborted());
        verify(songStoragePort).abortUpload(pending.getStagingKey(), "mpu-1");
        verify(songStoragePort).deleteObject(pending.getStagingKey());
    }

    @Test
    void reconcileExpiredUploads_singlePartUpload_hasNothingToAbortButStillDeletedAndAborted() {
        SongUpload singlePart = expiredPending(null);
        when(songUploadRepository.findExpiredByState(SongUploadState.PENDING_UPLOAD, NOW, 50))
                .thenReturn(List.of(singlePart));
        when(songUploadRepository.findExpiredByState(SongUploadState.COMPLETING, NOW, 50))
                .thenReturn(List.of());
        when(songUploadRepository.markAbortedFromPendingOrExpiredCompleting(singlePart.getSongId(), NOW))
                .thenReturn(true);

        var summary = service.reconcileExpiredUploads();

        assertEquals(1, summary.pendingAborted());
        verify(songStoragePort, never()).abortUpload(any(), any());
        verify(songStoragePort).deleteObject(singlePart.getStagingKey());
    }

    @Test
    void reconcileExpiredUploads_brokenCandidate_doesNotBlockTheBatch() {
        SongUpload broken = expiredPending(null);
        SongUpload healthy = expiredPending("mpu-2");
        when(songUploadRepository.findExpiredByState(SongUploadState.PENDING_UPLOAD, NOW, 50))
                .thenReturn(List.of(broken, healthy));
        when(songUploadRepository.findExpiredByState(SongUploadState.COMPLETING, NOW, 50))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("S3 down"))
                .when(songStoragePort).deleteObject(broken.getStagingKey());
        when(songUploadRepository.markAbortedFromPendingOrExpiredCompleting(healthy.getSongId(), NOW))
                .thenReturn(true);
        when(songUploadRepository.markAbortedFromPendingOrExpiredCompleting(broken.getSongId(), NOW))
                .thenReturn(false);

        var summary = service.reconcileExpiredUploads();

        assertEquals(1, summary.pendingAborted(),
                "the batch continues past a candidate whose storage delete fails");
        verify(songUploadRepository).markAbortedFromPendingOrExpiredCompleting(healthy.getSongId(),
                NOW);
    }

    @Test
    void reconcileExpiredUploads_staleCompletingLease_isReconciledThroughTheSamePass() {
        SongUpload completing = expiredPending("mpu-3");
        completing.markCompleting(NOW.minusSeconds(60)); // lease expired
        when(songUploadRepository.findExpiredByState(SongUploadState.PENDING_UPLOAD, NOW, 50))
                .thenReturn(List.of());
        when(songUploadRepository.findExpiredByState(SongUploadState.COMPLETING, NOW, 50))
                .thenReturn(List.of(completing));
        when(songUploadRepository.markAbortedFromPendingOrExpiredCompleting(completing.getSongId(), NOW))
                .thenReturn(true);

        var summary = service.reconcileExpiredUploads();

        assertEquals(1, summary.completingAborted());
        verify(songStoragePort).abortUpload(completing.getStagingKey(), "mpu-3");
    }
}
