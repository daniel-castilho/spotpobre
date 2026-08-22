package com.spotpobre.backend.application.idempotency;

import com.spotpobre.backend.domain.idempotency.model.IdempotencyRecord;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of attempting to claim a logical operation for a given scope.
 *
 * <ul>
 *   <li>{@link #claimed()} — this caller won; execute the business write once with
 *       {@code claim.resourceId()} (or recover it first when {@code recoveredFromPreviousAttempt}).</li>
 *   <li>{@link #replay()} — COMPLETED record: return the stored safe snapshot.</li>
 *   <li>{@link #replayedFailure()} — FAILED_FINAL record: replay the deterministic 4xx.</li>
 *   <li>{@link #activeLeaseElsewhere()} — another instance holds the live lease:
 *       map to 409 + Retry-After (seconds until {@code leaseUntil}).</li>
 *   <li>{@link #keyReusedWithDifferentRequest()} — same key, different canonical request:
 *       deterministic 409.</li>
 * </ul>
 */
public final class ClaimOutcome {

    private final Claim claim;
    private final IdempotencyRecord replayedRecord;
    private final boolean claimed;
    private final boolean replay;
    private final boolean replayedFailure;
    private final boolean activeLeaseElsewhere;
    private final boolean keyReusedWithDifferentRequest;

    private ClaimOutcome(final Claim claim, final IdempotencyRecord replayedRecord,
                         final boolean claimed, final boolean replay, final boolean replayedFailure,
                         final boolean activeLeaseElsewhere, final boolean keyReused) {
        this.claim = claim;
        this.replayedRecord = replayedRecord;
        this.claimed = claimed;
        this.replay = replay;
        this.replayedFailure = replayedFailure;
        this.activeLeaseElsewhere = activeLeaseElsewhere;
        this.keyReusedWithDifferentRequest = keyReused;
    }

    static ClaimOutcome ofClaim(final Claim newClaim) {
        return new ClaimOutcome(Objects.requireNonNull(newClaim), null, true, false, false, false, false);
    }

    static ClaimOutcome ofReplay(final IdempotencyRecord completedRecord) {
        return new ClaimOutcome(null, Objects.requireNonNull(completedRecord), false, true, false, false, false);
    }

    static ClaimOutcome ofReplayedFailure(final IdempotencyRecord failedRecord) {
        return new ClaimOutcome(null, Objects.requireNonNull(failedRecord), false, false, true, false, false);
    }

    static ClaimOutcome ofActiveLease(final IdempotencyRecord inProgressRecord) {
        return new ClaimOutcome(null, Objects.requireNonNull(inProgressRecord), false, false, false, true, false);
    }

    static ClaimOutcome ofKeyReuse() {
        return new ClaimOutcome(null, null, false, false, false, false, true);
    }

    public Optional<Claim> claimed() {
        return claim == null ? Optional.empty() : Optional.of(claim);
    }

    public Optional<IdempotencyRecord> replay() {
        return replay ? Optional.of(replayedRecord) : Optional.empty();
    }

    public Optional<IdempotencyRecord> replayedFailure() {
        return replayedFailure ? Optional.of(replayedRecord) : Optional.empty();
    }

    /**
     * The IN_PROGRESS record held under a live foreign lease; used by callers together with the
     * coordinator's clock-aware {@code retryAfterSecondsFor} to produce a Retry-After hint.
     */
    public Optional<IdempotencyRecord> activeLease() {
        return activeLeaseElsewhere ? Optional.of(replayedRecord) : Optional.empty();
    }

    public boolean isActiveLeaseElsewhere() {
        return activeLeaseElsewhere;
    }

    public boolean isKeyReusedWithDifferentRequest() {
        return keyReusedWithDifferentRequest;
    }
}
