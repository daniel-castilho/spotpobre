package com.spotpobre.backend.domain.song.port;

import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.model.SongUpload;
import com.spotpobre.backend.domain.song.model.SongUploadState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for durable song-upload records (spec §7). All transitions are conditional
 * compare-and-swap operations so racing confirmations/cleanups elect exactly one winner.
 */
public interface SongUploadRepository {

    /** Creates the record only if no upload exists for this song id yet. */
    boolean insertIfAbsent(SongUpload upload);

    Optional<SongUpload> findBySongId(SongId songId);

    /**
     * Conditionally moves the upload into COMPLETING under an exclusive lease. Succeeds from
     * {@code PENDING_UPLOAD} or by taking over an <b>expired</b> COMPLETING lease; fails while
     * another instance holds a live lease.
     */
    boolean acquireCompletingLease(SongId songId, Instant newLeaseUntil, Instant now);

    /** COMPLETING → COMPLETED, conditional on still holding exactly {@code expectedLeaseUntil}. */
    boolean markCompleted(SongId songId, Instant expectedLeaseUntil, Instant at);

    /** COMPLETING → PENDING_UPLOAD, conditional on holding {@code expectedLeaseUntil}. */
    boolean releaseCompletingLease(SongId songId, Instant expectedLeaseUntil, Instant at);

    /** Terminal abort for cleanup/reconciliation; never overwrites COMPLETED. */
    boolean markAbortedFromPendingOrExpiredCompleting(SongId songId, Instant now);

    /**
     * Atomically marks the upload COMPLETED (conditional on the caller's lease) and creates the
     * Song row if absent — one transaction, so a song never exists without a completed upload
     * and vice versa.
     */
    boolean markCompletedAndCreateSongIfAbsent(SongUpload upload, Song song, Instant at);

    /** Bounded cleanup-candidate scan through the {@code state-expiry-index} GSI. */
    List<SongUpload> findExpiredByState(SongUploadState state, Instant expiryCutoff, int limit);
}
