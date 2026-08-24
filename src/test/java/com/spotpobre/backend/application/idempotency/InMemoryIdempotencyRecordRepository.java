package com.spotpobre.backend.application.idempotency;

import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.FailureDescriptor;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyRecord;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyState;
import com.spotpobre.backend.domain.idempotency.model.LeaseToken;
import com.spotpobre.backend.domain.idempotency.model.ResultSnapshot;
import com.spotpobre.backend.domain.idempotency.port.IdempotencyRecordRepository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory fake of the idempotency repository for coordinator unit tests. Mirrors the
 * conditional semantics of the DynamoDB adapter: every mutation re-checks the currently stored
 * record before applying, so lost-lease and racing-takeover behavior is faithful.
 */
public final class InMemoryIdempotencyRecordRepository implements IdempotencyRecordRepository {

    private final Map<String, IdempotencyRecord> store = new ConcurrentHashMap<>();

    @Override
    public Optional<IdempotencyRecord> findByScopeKey(final String scopeKey) {
        return Optional.ofNullable(store.get(scopeKey));
    }

    @Override
    public boolean insertInProgress(final IdempotencyRecord record) {
        return store.putIfAbsent(record.scopeKey(), record) == null;
    }

    @Override
    public boolean takeoverExpiredLease(final String scopeKey, final CanonicalRequestHash expectedRequestHash,
                                        final LeaseToken newLease, final Instant newLeaseUntil,
                                        final Instant now) {
        AtomicReference<IdempotencyRecord> updated = new AtomicReference<>();
        store.computeIfPresent(scopeKey, (k, current) -> {
            if (current.state() != IdempotencyState.IN_PROGRESS
                    || !current.matches(expectedRequestHash)
                    || current.leaseActiveAt(now)) {
                return current;
            }
            IdempotencyRecord takenOver = IdempotencyRecord.builder()
                    .merge(current)
                    .lease(newLease)
                    .leaseUntil(newLeaseUntil)
                    .updatedAt(now)
                    .build();
            updated.set(takenOver);
            return takenOver;
        });
        return updated.get() != null;
    }

    @Override
    public boolean replaceLogicallyExpired(final String scopeKey, final IdempotencyRecord replacement,
                                           final Instant now) {
        AtomicReference<Boolean> replaced = new AtomicReference<>(false);
        store.compute(scopeKey, (k, current) -> {
            if (current != null && !current.logicallyExpiredAt(now)) {
                return current;
            }
            replaced.set(true);
            return replacement;
        });
        return replaced.get();
    }

    /**
     * Fault-injection seam (spec S8/S23): when {@code true}, the next conditional transition
     * ({@code markCompleted}/{@code markFailedFinal}/{@code releaseInProgress}) reports
     * {@code false} — simulating a lost lease — without mutating state. Reset automatically
     * after one suppressed call.
     */
    public final AtomicBoolean failNextConditionalTransition = new AtomicBoolean(false);

    private boolean suppressNextIfArmed() {
        return failNextConditionalTransition.compareAndSet(true, false);
    }

    @Override
    public boolean markCompleted(final String scopeKey, final LeaseToken currentLease,
                                 final ResultSnapshot snapshot, final Instant completedAt,
                                 final Instant updatedAt) {
        if (suppressNextIfArmed()) {
            return false;
        }
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        store.computeIfPresent(scopeKey, (k, current) -> {
            if (!isLeasedTo(current, currentLease)) {
                return current;
            }
            done.set(true);
            return IdempotencyRecord.builder()
                    .merge(current)
                    .state(IdempotencyState.COMPLETED)
                    .resultSnapshot(snapshot)
                    .completedAt(completedAt)
                    .updatedAt(updatedAt)
                    .build();
        });
        return done.get();
    }

    @Override
    public boolean markFailedFinal(final String scopeKey, final LeaseToken currentLease,
                                   final FailureDescriptor failure, final Instant at) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        store.computeIfPresent(scopeKey, (k, current) -> {
            if (!isLeasedTo(current, currentLease)) {
                return current;
            }
            done.set(true);
            return IdempotencyRecord.builder()
                    .merge(current)
                    .state(IdempotencyState.FAILED_FINAL)
                    .failure(failure)
                    .updatedAt(at)
                    .build();
        });
        return done.get();
    }

    @Override
    public boolean releaseInProgress(final String scopeKey, final LeaseToken currentLease) {
        AtomicReference<Boolean> released = new AtomicReference<>(false);
        store.compute(scopeKey, (k, current) -> {
            if (current == null || !isLeasedTo(current, currentLease)) {
                return current;
            }
            released.set(true);
            return null;
        });
        return released.get();
    }

    /** Test helper: forces the stored lease deadline forward/backward to simulate clock passage or a crashed attempt. */
    public void expireLease(final String scopeKey, final Instant newLeaseUntil) {
        store.computeIfPresent(scopeKey, (k, current) -> IdempotencyRecord.builder()
                .merge(current)
                .leaseUntil(newLeaseUntil)
                .build());
    }

    /** Test helper: forces the whole-record TTL expiry to simulate a logically expired record. */
    public void expireLogically(final String scopeKey, final long expiresAtEpochSeconds) {
        store.computeIfPresent(scopeKey, (k, current) -> IdempotencyRecord.builder()
                .merge(current)
                .expiresAtEpochSeconds(expiresAtEpochSeconds)
                .build());
    }

    private static boolean isLeasedTo(final IdempotencyRecord record, final LeaseToken lease) {
        return record != null
                && record.state() == IdempotencyState.IN_PROGRESS
                && record.lease() != null
                && record.lease().equals(lease);
    }
}
