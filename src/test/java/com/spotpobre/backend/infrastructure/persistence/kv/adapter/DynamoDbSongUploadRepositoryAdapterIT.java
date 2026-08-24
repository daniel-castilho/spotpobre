package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.AbstractIntegrationTest;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.model.SongUpload;
import com.spotpobre.backend.domain.song.model.SongUploadState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DynamoDbSongUploadRepositoryAdapterIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-23T12:00:00Z");

    @Autowired
    private DynamoDbSongUploadRepositoryAdapter adapter;

    @Test
    void insertIfAbsent_duplicateSongId_secondInsertRejected() {
        SongUpload upload = freshUpload(NOW.plus(java.time.Duration.ofHours(24)));

        assertTrue(adapter.insertIfAbsent(upload));
        assertFalse(adapter.insertIfAbsent(freshUploadWithSameSong(upload)),
                "a second insert for the same reserved song id must be rejected");
    }

    @Test
    void acquireCompletingLease_pendingUpload_leaseAcquiredAndLiveLeaseBlocksOthers() {
        SongUpload upload = freshUpload(NOW.plus(java.time.Duration.ofHours(24)));
        adapter.insertIfAbsent(upload);

        Instant firstLease = NOW.plusSeconds(120);
        assertTrue(adapter.acquireCompletingLease(upload.getSongId(), firstLease, NOW));

        // A live lease blocks any other acquirer.
        assertFalse(adapter.acquireCompletingLease(upload.getSongId(), NOW.plusSeconds(300), NOW));

        Optional<SongUpload> stored = adapter.findBySongId(upload.getSongId());
        assertEquals(SongUploadState.COMPLETING, stored.orElseThrow().getState());
        assertEquals(firstLease, stored.orElseThrow().getCompletingLeaseUntil());
    }

    @Test
    void acquireCompletingLease_expiredLease_takeoverSucceedsAndStaleHolderLosesCompletion() {
        SongUpload upload = freshUpload(NOW.plus(java.time.Duration.ofHours(24)));
        adapter.insertIfAbsent(upload);

        Instant staleLease = NOW.minusSeconds(1);
        assertTrue(adapter.acquireCompletingLease(upload.getSongId(), staleLease,
                NOW.minusSeconds(121)), "first acquisition from PENDING_UPLOAD must succeed");

        // Expired: a second worker takes over with its own lease.
        Instant takeoverLease = NOW.plusSeconds(120);
        assertTrue(adapter.acquireCompletingLease(upload.getSongId(), takeoverLease, NOW),
                "expired COMPLETING lease must be takeable over");

        // The stale holder's completion must be rejected (lease mismatch).
        assertFalse(adapter.markCompleted(upload.getSongId(), staleLease, NOW));
        assertTrue(adapter.markCompleted(upload.getSongId(), takeoverLease, NOW));
        assertEquals(SongUploadState.COMPLETED,
                adapter.findBySongId(upload.getSongId()).orElseThrow().getState());
    }

    @Test
    void releaseCompletingLease_wrongTokenRejected_rightTokenReturnsToPending() {
        SongUpload upload = freshUpload(NOW.plus(java.time.Duration.ofHours(24)));
        adapter.insertIfAbsent(upload);
        Instant lease = NOW.plusSeconds(120);
        adapter.acquireCompletingLease(upload.getSongId(), lease, NOW);

        assertFalse(adapter.releaseCompletingLease(upload.getSongId(), NOW.plusSeconds(999), NOW),
                "wrong lease token must not release someone else's lease");
        assertTrue(adapter.releaseCompletingLease(upload.getSongId(), lease, NOW));
        assertEquals(SongUploadState.PENDING_UPLOAD,
                adapter.findBySongId(upload.getSongId()).orElseThrow().getState());
    }

    @Test
    void markAbortedFromPendingOrExpiredCompleting_completedIsUntouchable() {
        SongUpload completed = freshUpload(NOW.plus(java.time.Duration.ofHours(24)));
        adapter.insertIfAbsent(completed);
        adapter.acquireCompletingLease(completed.getSongId(), NOW.plusSeconds(120), NOW);
        adapter.markCompleted(completed.getSongId(), NOW.plusSeconds(120), NOW);

        assertFalse(adapter.markAbortedFromPendingOrExpiredCompleting(completed.getSongId(), NOW),
                "COMPLETED uploads are terminal and must never be aborted by cleanup");

        SongUpload pending = freshUpload(NOW.minusSeconds(1));
        adapter.insertIfAbsent(pending);
        assertTrue(adapter.markAbortedFromPendingOrExpiredCompleting(pending.getSongId(), NOW));
        assertEquals(SongUploadState.ABORTED,
                adapter.findBySongId(pending.getSongId()).orElseThrow().getState());
    }

    @Test
    void findExpiredByState_returnsOnlyRecordsPastTheCutoff() {
        SongUpload expired = freshUpload(NOW.minusSeconds(1));
        SongUpload alive = freshUpload(NOW.plus(java.time.Duration.ofHours(24)));
        adapter.insertIfAbsent(expired);
        adapter.insertIfAbsent(alive);

        List<SongUpload> candidates =
                adapter.findExpiredByState(SongUploadState.PENDING_UPLOAD, NOW, 50);

        assertTrue(candidates.stream().anyMatch(u -> u.getSongId().equals(expired.getSongId())),
                "record whose logical expiry passed must appear in the cleanup scan");
        assertTrue(candidates.stream().noneMatch(u -> u.getSongId().equals(alive.getSongId())),
                "not-yet-expired records must stay out of the bounded cleanup scan");
    }


    private static SongUpload freshUpload(final Instant logicalExpiry) {
        return SongUpload.start(new SongId(UUID.randomUUID()), "Track", new AlbumId(UUID.randomUUID()),
                new ArtistId(UUID.randomUUID()), UUID.randomUUID(), "audio/mpeg", 1024L,
                null, NOW, logicalExpiry);
    }

    private static SongUpload freshUploadWithSameSong(final SongUpload template) {
        return SongUpload.start(template.getSongId(), template.getTitle(), template.getAlbumId(),
                template.getArtistId(), template.getActorUserId(), template.getContentType(),
                template.getContentLengthBytes(), null, NOW, NOW.plusSeconds(3600));
    }
}
