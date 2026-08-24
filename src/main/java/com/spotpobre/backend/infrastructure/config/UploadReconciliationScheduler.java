package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.application.song.port.in.ReconcileExpiredUploadsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic driver for the bounded upload-reconciliation pass (spec S16). Multiple instances
 * are safe: every terminal transition is a conditional write, so racing schedulers converge
 * without double-aborting. Metrics-friendly counters stay in the application summary log.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "spotpobre.uploads.reconciliation-enabled", havingValue = "true",
        matchIfMissing = true)
public class UploadReconciliationScheduler {

    private final ReconcileExpiredUploadsUseCase reconcileExpiredUploadsUseCase;

    @Scheduled(fixedDelayString = "${spotpobre.uploads.reconciliation-interval-ms:3600000}",
            initialDelayString = "${spotpobre.uploads.reconciliation-initial-delay-ms:120000}")
    public void reconcile() {
        try {
            var summary = reconcileExpiredUploadsUseCase.reconcileExpiredUploads();
            if (summary.pendingAborted() > 0 || summary.completingAborted() > 0) {
                log.info("Expired upload reconciliation: {} pending + {} completing aborted",
                        summary.pendingAborted(), summary.completingAborted());
            }
        } catch (RuntimeException e) {
            log.warn("Upload reconciliation pass failed: {}", e.getMessage(), e);
        }
    }
}
