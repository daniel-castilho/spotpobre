package com.spotpobre.backend.application.idempotency;

import com.spotpobre.backend.application.idempotency.port.out.IdempotencyMetrics;
import com.spotpobre.backend.domain.common.Digests;
import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.FailureDescriptor;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyRecord;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyScope;
import com.spotpobre.backend.domain.idempotency.model.LeaseToken;
import com.spotpobre.backend.domain.idempotency.model.ResultSnapshot;
import com.spotpobre.backend.domain.idempotency.port.IdempotencyRecordRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements the durable claim-and-lease protocol (spec §5.4–§5.6) on top of the conditional
 * repository port:
 *
 * <ol>
 *   <li>try a fresh IN_PROGRESS claim with a stable preassigned resource ID;</li>
 *   <li>on conflict inspect the stored record — replay completed results, replay deterministic
 *       failures, surface 409+Retry-After for active foreign leases, or take over expired leases
 *       preserving the resource ID;</li>
 *   <li>replace logically-expired records (TTL reached) conditionally;</li>
 *   <li>complete/fail/release only while the caller still owns the lease.</li>
 * </ol>
 *
 * <p><b>Fault-injection seam:</b> crash-recovery scenarios are exercised by driving these
 * primitives directly in tests — claim, abandon (simulated crash), advance the clock past the
 * lease, re-claim (takeover) and verify the same resource ID is returned. No business write is
 * ever executed inside this coordinator, so no additional hooks are required.</p>
 */
public class IdempotencyCoordinator {

    public static final Duration DEFAULT_CREATION_LEASE = Duration.ofSeconds(30);
    public static final Duration UPLOAD_LEASE = Duration.ofSeconds(120);
    private static final long DEFAULT_TTL_SECONDS = Duration.ofHours(24).toSeconds();

    private final IdempotencyRecordRepository repository;
    private final Clock clock;
    private final IdempotencyMetrics metrics;

    public IdempotencyCoordinator(final IdempotencyRecordRepository repository, final Clock clock,
                                  final IdempotencyMetrics metrics) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.metrics = Objects.requireNonNull(metrics, "metrics is required");
    }

    /**
     * Attempts to claim the logical operation identified by {@code scope}.
     *
     * @param leaseDuration 30 s for normal creations, 120 s for upload initiation/confirmation
     */
    public ClaimOutcome claim(final IdempotencyScope scope, final CanonicalRequestHash requestHash,
                              final String operationName, final IdempotencyResourceType resourceType,
                              final Duration leaseDuration) {
        Objects.requireNonNull(scope, "scope is required");
        Objects.requireNonNull(requestHash, "requestHash is required");
        Objects.requireNonNull(resourceType, "resourceType is required");
        Objects.requireNonNull(leaseDuration, "leaseDuration is required");
        if (leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }

        final Instant now = clock.instant();
        final String scopeKey = scope.scopeKey();

        final LeaseToken freshLease = LeaseToken.generate();
        final IdempotencyRecord fresh = newClaim(scope, requestHash, operationName, resourceType,
                UUID.randomUUID().toString(), freshLease, now, leaseDuration);

        if (repository.insertInProgress(fresh)) {
            metrics.incrementClaimOutcome(IdempotencyMetrics.ClaimOutcomeTag.CLAIMED_NEW);
            return ClaimOutcome.ofClaim(new Claim(scopeKey, fresh.resourceId(), freshLease, false));
        }

        final Optional<IdempotencyRecord> existing = repository.findByScopeKey(scopeKey);
        if (existing.isEmpty()) {
            // Raced with a release/delete of the stale record: retry once.
            if (repository.insertInProgress(fresh)) {
                metrics.incrementClaimOutcome(IdempotencyMetrics.ClaimOutcomeTag.CLAIMED_NEW);
                return ClaimOutcome.ofClaim(new Claim(scopeKey, fresh.resourceId(), freshLease, false));
            }
            return ClaimOutcome.ofActiveLease(reloadOrSelf(scopeKey, fresh));
        }

        final IdempotencyRecord record = existing.get();

        // Logical expiry precedes state handling: an expired record may be replaced outright
        // regardless of its state (DynamoDB TTL deletion is eventual; we never wait for it).
        if (record.logicallyExpiredAt(now)) {
            final LeaseToken replacementLease = LeaseToken.generate();
            final IdempotencyRecord replacement = newClaim(scope, requestHash, operationName,
                    resourceType, UUID.randomUUID().toString(), replacementLease, now, leaseDuration);
            if (repository.replaceLogicallyExpired(scopeKey, replacement, now)) {
                metrics.incrementClaimOutcome(IdempotencyMetrics.ClaimOutcomeTag.CLAIMED_NEW);
                return ClaimOutcome.ofClaim(new Claim(scopeKey, replacement.resourceId(), replacementLease, false));
            }
            return ClaimOutcome.ofActiveLease(reloadOrSelf(scopeKey, replacement));
        }

        if (!record.matches(requestHash)) {
            metrics.incrementClaimOutcome(IdempotencyMetrics.ClaimOutcomeTag.KEY_REUSED);
            return ClaimOutcome.ofKeyReuse();
        }

        return switch (record.state()) {
            case COMPLETED -> {
                metrics.incrementClaimOutcome(IdempotencyMetrics.ClaimOutcomeTag.REPLAY_COMPLETED);
                yield ClaimOutcome.ofReplay(record);
            }
            case FAILED_FINAL -> {
                metrics.incrementClaimOutcome(IdempotencyMetrics.ClaimOutcomeTag.REPLAY_FAILED_FINAL);
                yield ClaimOutcome.ofReplayedFailure(record);
            }
            case IN_PROGRESS -> handleInProgress(scopeKey, requestHash, record, operationName, scope,
                    resourceType, now, leaseDuration);
        };
    }

    private ClaimOutcome handleInProgress(final String scopeKey, final CanonicalRequestHash requestHash,
                                          final IdempotencyRecord record, final String operationName,
                                          final IdempotencyScope scope,
                                          final IdempotencyResourceType resourceType,
                                          final Instant now, final Duration leaseDuration) {
        if (record.leaseActiveAt(now)) {
            metrics.incrementClaimOutcome(IdempotencyMetrics.ClaimOutcomeTag.ACTIVE_LEASE);
            return ClaimOutcome.ofActiveLease(record);
        }

        // Expired lease: conditional takeover preserving the reserved resource ID so a crashed
        // attempt can be recovered without duplicating the resource.
        final LeaseToken takeoverLease = LeaseToken.generate();
        final Instant newLeaseUntil = now.plus(leaseDuration);
        if (repository.takeoverExpiredLease(scopeKey, requestHash, takeoverLease, newLeaseUntil, now)) {
            metrics.incrementClaimOutcome(IdempotencyMetrics.ClaimOutcomeTag.RECOVERED_CLAIM);
            return ClaimOutcome.ofClaim(new Claim(scopeKey, record.resourceId(), takeoverLease, true));
        }

        return ClaimOutcome.ofActiveLease(reloadOrSelf(scopeKey, record));
    }

    /**
     * Marks a claimed operation COMPLETED. Conditional on still holding the lease — a caller
     * that lost its lease must not overwrite another instance's result.
     *
     * @return {@code true} when this caller's snapshot became durable.
     */
    public boolean completeClaim(final Claim claim, final ResultSnapshot snapshot, final Instant at) {
        Objects.requireNonNull(claim, "claim is required");
        boolean done = repository.markCompleted(claim.scopeKey(), claim.lease(), snapshot, at, at);
        metrics.incrementTransition(done
                ? IdempotencyMetrics.TransitionTag.COMPLETED
                : IdempotencyMetrics.TransitionTag.LOST_LEASE);
        return done;
    }

    /**
     * Marks a deterministic post-claim 4xx failure as FAILED_FINAL (replayable).
     *
     * @return {@code true} when this caller's failure became durable.
     */
    public boolean failClaim(final Claim claim, final FailureDescriptor failure, final Instant at) {
        Objects.requireNonNull(claim, "claim is required");
        boolean done = repository.markFailedFinal(claim.scopeKey(), claim.lease(), failure, at);
        metrics.incrementTransition(done
                ? IdempotencyMetrics.TransitionTag.FAILED_FINAL
                : IdempotencyMetrics.TransitionTag.LOST_LEASE);
        return done;
    }

    /**
     * Releases (deletes) an IN_PROGRESS claim when no business write happened — immediate
     * retries start fresh instead of waiting out the lease. Infrastructure/unknown failures that
     * occurred after a possible business write should NOT be released: retain the record so a
     * retry recovers through takeover instead of duplicating the resource.
     *
     * @return {@code true} when released here; {@code false} when the lease was already lost.
     */
    public boolean releaseClaim(final Claim claim, final Instant at) {
        Objects.requireNonNull(claim, "claim is required");
        boolean released = repository.releaseInProgress(claim.scopeKey(), claim.lease());
        metrics.incrementTransition(released
                ? IdempotencyMetrics.TransitionTag.RELEASED
                : IdempotencyMetrics.TransitionTag.LOST_LEASE);
        return released;
    }

    /** Retry-After hint (seconds) for an ACTIVE_LEASE outcome. */
    public long retryAfterSecondsFor(final IdempotencyRecord activeLeaseRecord, final Duration fallback) {
        if (activeLeaseRecord == null || activeLeaseRecord.leaseUntil() == null) {
            return Math.max(1, fallback.getSeconds());
        }
        long seconds = activeLeaseRecord.leaseUntil().getEpochSecond() - clock.instant().getEpochSecond();
        return Math.max(1, seconds);
    }

    private IdempotencyRecord reloadOrSelf(final String scopeKey, final IdempotencyRecord self) {
        return repository.findByScopeKey(scopeKey).orElse(self);
    }

    private IdempotencyRecord newClaim(final IdempotencyScope scope, final CanonicalRequestHash requestHash,
                                       final String operationName, final IdempotencyResourceType resourceType,
                                       final String resourceId, final LeaseToken lease,
                                       final Instant now, final Duration leaseDuration) {
        return IdempotencyRecord.builder()
                .scopeKey(scope.scopeKey())
                .operationName(operationName)
                .routeTemplate(scope.routeTemplate())
                .actorScopeHash(Digests.sha256Hex(scope.actorScope()))
                .requestHash(requestHash)
                .state(com.spotpobre.backend.domain.idempotency.model.IdempotencyState.IN_PROGRESS)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .lease(lease)
                .leaseUntil(now.plus(leaseDuration))
                .createdAt(now)
                .updatedAt(now)
                .expiresAtEpochSeconds(now.getEpochSecond() + DEFAULT_TTL_SECONDS)
                .build();
    }
}
