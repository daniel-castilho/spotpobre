package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.ReconcileExpiredUploadsUseCase;
import com.spotpobre.backend.domain.song.model.SongUpload;
import com.spotpobre.backend.domain.song.model.SongUploadState;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import com.spotpobre.backend.domain.song.port.SongUploadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Bounded cleanup/reconciliation of expired uploads (spec S16): candidates are read through
 * the {@code state-expiry-index} GSI (never a table scan); each candidate's staging bytes are
 * removed and its multipart attempt aborted before the terminal ABORTED transition, which is a
 * conditional write so repeated or concurrent passes stay idempotent.
 */
@RequiredArgsConstructor
@Slf4j
public class ReconcileExpiredUploadsService implements ReconcileExpiredUploadsUseCase {

    static final int DEFAULT_BATCH_SIZE = 50;

    private final SongUploadRepository songUploadRepository;
    private final SongStoragePort songStoragePort;
    private final Clock clock;

    @Override
    public ReconciliationSummary reconcileExpiredUploads() {
        final Instant now = clock.instant();
        final int pendingAborted =
                reconcile(SongUploadState.PENDING_UPLOAD,
                        songUploadRepository.findExpiredByState(SongUploadState.PENDING_UPLOAD,
                                now, DEFAULT_BATCH_SIZE), now);
        final int completingAborted =
                reconcile(SongUploadState.COMPLETING,
                        songUploadRepository.findExpiredByState(SongUploadState.COMPLETING,
                                now, DEFAULT_BATCH_SIZE), now);
        return new ReconciliationSummary(pendingAborted, completingAborted);
    }

    private int reconcile(final SongUploadState state, final List<SongUpload> candidates,
                          final Instant now) {
        int aborted = 0;
        for (SongUpload upload : candidates) {
            try {
                if (upload.getMultipartUploadId() != null) {
                    songStoragePort.abortUpload(upload.getStagingKey(), upload.getMultipartUploadId());
                }
                songStoragePort.deleteObject(upload.getStagingKey());
                if (songUploadRepository.markAbortedFromPendingOrExpiredCompleting(
                        upload.getSongId(), now)) {
                    aborted++;
                }
            } catch (RuntimeException e) {
                // Best-effort per candidate: one broken object must not block the batch; the
                // conditional abort keeps the record eligible for the next pass.
                log.warn("Upload reconciliation skipped for song {}: {}", upload.getSongId(),
                        e.getMessage());
            }
        }
        if (aborted > 0) {
            log.info("Upload reconciliation aborted {} {} record(s)", aborted, state);
        }
        return aborted;
    }
}
