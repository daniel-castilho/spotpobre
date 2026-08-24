package com.spotpobre.backend.application.song.port.in;

/**
 * Cleanup/reconciliation entry point for expired song uploads (spec S16). Bounded by design:
 * candidates come from the {@code state-expiry-index} GSI, never a full table scan; concurrent
 * workers are safe because every terminal transition is a conditional write.
 */
public interface ReconcileExpiredUploadsUseCase {

    /**
     * Runs one bounded reconciliation pass over expired PENDING_UPLOAD and stale COMPLETING
     * records.
     *
     * @return per-state counts of uploads transitioned to ABORTED
     */
    ReconciliationSummary reconcileExpiredUploads();

    record ReconciliationSummary(int pendingAborted, int completingAborted) {
    }
}
