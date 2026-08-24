package com.spotpobre.backend.domain.song.model;

import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongUploadTest {

    private static final Instant T0 = Instant.parse("2026-08-23T10:00:00Z");

    private static SongUpload freshUpload() {
        return SongUpload.start(new SongId(UUID.randomUUID()), new AlbumId(UUID.randomUUID()),
                new ArtistId(UUID.randomUUID()), UUID.randomUUID(), "audio/mpeg", 1024L,
                null, T0, T0.plus(java.time.Duration.ofHours(24)));
    }

    @Test
    void start_derivesServerSideKeysAndStartsPending() {
        SongId songId = new SongId(UUID.randomUUID());

        SongUpload upload = SongUpload.start(songId, new AlbumId(UUID.randomUUID()),
                new ArtistId(UUID.randomUUID()), UUID.randomUUID(), "audio/mpeg", 2048L,
                null, T0, null);

        assertEquals(SongUploadState.PENDING_UPLOAD, upload.getState());
        assertEquals("pending/" + songId.value(), upload.getStagingKey(),
                "staging key must be derived server-side");
        assertEquals("songs/" + songId.value(), upload.getFinalKey());
        assertNull(upload.getMultipartUploadId());
        assertFalse(upload.completingLeaseActiveAt(T0));
    }

    @Test
    void legalTransitions_pendingToCompletingToCompleted_succeed() {
        SongUpload upload = freshUpload();

        upload.markCompleting(T0.plusSeconds(120));
        assertTrue(upload.completingLeaseActiveAt(T0.plusSeconds(60)));

        upload.markCompleted();
        assertEquals(SongUploadState.COMPLETED, upload.getState());
    }

    @Test
    void completingFailure_releasesBackToPending_andCanBeRetried() {
        SongUpload upload = freshUpload();

        upload.markCompleting(T0.plusSeconds(120));
        upload.markReleasedFromCompleting();

        assertEquals(SongUploadState.PENDING_UPLOAD, upload.getState());
        assertNull(upload.getCompletingLeaseUntil());
        assertFalse(upload.completingLeaseActiveAt(T0));
    }

    @Test
    void abort_isReachableFromPendingAndCompleting_butNotFromCompleted() {
        SongUpload pending = freshUpload();
        pending.markAborted();
        assertEquals(SongUploadState.ABORTED, pending.getState());

        SongUpload completing = freshUpload();
        completing.markCompleting(T0.plusSeconds(120));
        completing.markAborted();
        assertEquals(SongUploadState.ABORTED, completing.getState());

        SongUpload completed = freshUpload();
        completed.markCompleting(T0.plusSeconds(120));
        completed.markCompleted();
        assertThrows(IllegalStateException.class, completed::markAborted);
    }

    @Test
    void illegalTransition_completedCannotGoBackToCompleting() {
        SongUpload upload = freshUpload();
        upload.markCompleting(T0.plusSeconds(120));
        upload.markCompleted();

        assertThrows(IllegalStateException.class,
                () -> upload.markCompleting(T0.plusSeconds(120)));
        assertThrows(IllegalStateException.class, upload::markReleasedFromCompleting);
    }

    @Test
    void multipartId_cannotBeChangedOnceBound() {
        SongUpload upload = freshUpload();
        upload.attachMultipartUploadId("mpu-1");

        assertThrows(IllegalStateException.class,
                () -> upload.attachMultipartUploadId("mpu-2"));
        assertEquals("mpu-1", upload.getMultipartUploadId());

        // Re-binding the same id is a no-op (crash recovery reuse).
        upload.attachMultipartUploadId("mpu-1");
        assertEquals("mpu-1", upload.getMultipartUploadId());
    }

    @Test
    void multipartId_cannotBeAttachedAfterCompletion() {
        SongUpload upload = freshUpload();
        upload.markCompleting(T0.plusSeconds(120));
        upload.markCompleted();

        assertThrows(IllegalStateException.class,
                () -> upload.attachMultipartUploadId("late-mpu"));
    }
}
