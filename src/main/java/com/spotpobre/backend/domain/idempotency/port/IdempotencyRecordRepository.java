package com.spotpobre.backend.domain.idempotency.port;

import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.FailureDescriptor;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyRecord;
import com.spotpobre.backend.domain.idempotency.model.LeaseToken;
import com.spotpobre.backend.domain.idempotency.model.ResultSnapshot;

import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port for the durable idempotency store. Implementations must guarantee the
 * conditional-write semantics described per method — every transition is guarded so that
 * concurrent claimants, stale leases and replayed requests converge to one logical outcome.
 *
 * <p>Only digests and safe snapshots are persisted; implementations never receive raw keys or
 * secrets.</p>
 */
public interface IdempotencyRecordRepository {

    Optional<IdempotencyRecord> findByScopeKey(String scopeKey);

    /**
     * Conditional create of a fresh {@code IN_PROGRESS} record.
     *
     * @return {@code true} when inserted; {@code false} when a record with the same scope key
     *         already exists (including a physically-expired-but-not-yet-deleted one).
     */
    boolean insertInProgress(IdempotencyRecord record);

    /**
     * Conditional takeover of an expired lease: succeeds only while
     * {@code state = IN_PROGRESS AND leaseUntil <= now AND requestHash unchanged}. The stored
     * resource ID is preserved — this is what makes crash recovery reuse the same resource.
     *
     * @return {@code true} when this caller owns the new lease; {@code false} when another
     *         instance won the race or the lease was still active.
     */
    boolean takeoverExpiredLease(String scopeKey, CanonicalRequestHash expectedRequestHash,
                                 LeaseToken newLease, Instant newLeaseUntil,
                                 Instant now);

    /**
     * Conditional replacement of a logically expired record (TTL attribute reached but DynamoDB
     * has not deleted the row yet). Any state may be replaced — the operation effectively starts
     * fresh with a newly reserved resource ID.
     *
     * @return {@code true} when replaced; {@code false} when another caller replaced/reused it first.
     */
    boolean replaceLogicallyExpired(String scopeKey, IdempotencyRecord replacement, Instant now);

    /**
     * Conditional completion: only while {@code state = IN_PROGRESS} and the caller still holds
     * the lease (hash match). A completed record is never overwritten.
     *
     * @return {@code true} when marked completed; {@code false} when the lease was lost/taken over.
     */
    boolean markCompleted(String scopeKey, LeaseToken currentLease, ResultSnapshot snapshot,
                          Instant completedAt, Instant updatedAt);

    /**
     * Conditional deterministic-failure marking; same lease guard as {@link #markCompleted}.
     *
     * @return {@code true} when marked FAILED_FINAL; {@code false} when the lease was lost.
     */
    boolean markFailedFinal(String scopeKey, LeaseToken currentLease, FailureDescriptor failure,
                            Instant at);

    /**
     * Conditional release (delete) of an IN_PROGRESS claim still owned by the given lease — used
     * by operations that know no business write happened and want immediate retry instead of
     * waiting out the lease.
     *
     * @return {@code true} when released here; {@code false} when the lease was already lost.
     */
    boolean releaseInProgress(String scopeKey, LeaseToken currentLease);
}
