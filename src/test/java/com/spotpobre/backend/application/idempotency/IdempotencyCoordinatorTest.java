package com.spotpobre.backend.application.idempotency;

import com.spotpobre.backend.domain.common.IdempotencyKey;
import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.FailureDescriptor;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyRecord;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyScope;
import com.spotpobre.backend.domain.idempotency.model.LeaseToken;
import com.spotpobre.backend.domain.idempotency.model.ResultSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyCoordinatorTest {

    private static final Duration LEASE = IdempotencyCoordinator.DEFAULT_CREATION_LEASE;
    private static final Instant T0 = Instant.parse("2026-01-15T10:00:00Z");

    private MutableClock clock;
    private InMemoryIdempotencyRecordRepository repository;
    private RecordingMetrics metrics;
    private IdempotencyCoordinator coordinator;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        repository = new InMemoryIdempotencyRecordRepository();
        metrics = new RecordingMetrics();
        coordinator = new IdempotencyCoordinator(repository, clock, metrics);
    }

    private static IdempotencyScope scope() {
        return new IdempotencyScope(
                "v1", "user:11111111-1111-1111-1111-111111111111", "POST",
                "/api/v1/playlists", "", IdempotencyKey.of("0123456789abcdef"));
    }

    private static CanonicalRequestHash requestHash() {
        return CanonicalRequestHash.current(List.of("name=Road trip", "ownerId=u-1"));
    }

    @Test
    void claim_newOperation_insertsInProgressAndReturnsClaimWithPreassignedResourceId() {
        ClaimOutcome outcome = coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE);

        assertTrue(outcome.claimed().isPresent());
        Claim claim = outcome.claimed().get();
        assertFalse(claim.recoveredFromPreviousAttempt());
        assertEquals(scope().scopeKey(), claim.scopeKey());

        Optional<IdempotencyRecord> stored = repository.findByScopeKey(claim.scopeKey());
        assertTrue(stored.isPresent());
        assertEquals(claim.resourceId(), stored.get().resourceId());
        assertEquals(T0.plus(LEASE), stored.get().leaseUntil());
    }

    @Test
    void claim_concurrentRacingCallers_exactlyOneWinsAndOthersSeeActiveLease() throws Exception {
        final int callers = 16;
        final ExecutorService pool = Executors.newFixedThreadPool(callers);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<ClaimOutcome>> futures = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                        IdempotencyResourceType.PLAYLIST, LEASE);
            }));
        }
        start.countDown();
        int claimed = 0;
        int activeLease = 0;
        for (Future<ClaimOutcome> future : futures) {
            ClaimOutcome outcome = future.get(10, TimeUnit.SECONDS);
            if (outcome.claimed().isPresent()) {
                claimed++;
            } else if (outcome.activeLease().isPresent()) {
                activeLease++;
            }
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, claimed, "exactly one caller must win the fresh claim");
        assertEquals(callers - 1, activeLease, "all other callers must observe an active lease");
    }

    @Test
    void claim_completedRecord_replaysStoredSnapshot() {
        Claim first = coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE).claimed().orElseThrow();
        ResultSnapshot snapshot = ResultSnapshot.jsonBody("{\"id\":\"p-123\"}");
        assertTrue(coordinator.completeClaim(first, snapshot, T0));

        ClaimOutcome second = coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE);

        assertTrue(second.replay().isPresent());
        assertEquals("{\"id\":\"p-123\"}", second.replay().get().resultSnapshot().body());
        assertEquals(first.resourceId(), second.replay().get().resourceId(),
                "replay must surface the original claim's resource ID");
        assertTrue(second.claimed().isEmpty());
    }

    @Test
    void claim_failedFinalRecord_replaysDeterministicFailure() {
        Claim first = coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE).claimed().orElseThrow();
        assertTrue(coordinator.failClaim(first,
                FailureDescriptor.of(409, "PLAYLIST_LIMIT_REACHED", "Playlist limit reached"), T0));

        ClaimOutcome second = coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE);

        assertTrue(second.replayedFailure().isPresent());
        assertEquals(409, second.replayedFailure().get().failure().status());
        assertTrue(second.claimed().isEmpty());
    }

    @Test
    void claim_sameKeyDifferentRequest_isKeyReuseConflict() {
        coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE);

        CanonicalRequestHash differentBody = CanonicalRequestHash.current(List.of("name=Other", "ownerId=u-1"));
        ClaimOutcome outcome = coordinator.claim(scope(), differentBody, "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE);

        assertTrue(outcome.isKeyReusedWithDifferentRequest());
    }

    @Test
    void claim_activeForeignLease_returnsActiveLeaseElsewhere() {
        coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE);

        // A different lease token (simulating another instance) still holds the live lease.
        ClaimOutcome outcome = coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE);

        assertTrue(outcome.isActiveLeaseElsewhere());
        long retryAfter = coordinator.retryAfterSecondsFor(outcome.activeLease().orElse(null), Duration.ofSeconds(5));
        assertTrue(retryAfter >= 1 && retryAfter <= LEASE.getSeconds());
    }

    @Test
    void claim_expiredLease_takeoverPreservesResourceIdAndFlagsRecovery() {
        Claim crashed = coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE).claimed().orElseThrow();

        clock.advance(LEASE.multipliedBy(2));

        ClaimOutcome takeover = coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE);

        assertTrue(takeover.claimed().isPresent());
        Claim recovered = takeover.claimed().get();
        assertTrue(recovered.recoveredFromPreviousAttempt());
        assertEquals(crashed.resourceId(), recovered.resourceId(),
                "takeover must preserve the reserved resource ID");
        assertNotEquals(crashed.lease(), recovered.lease());
    }

    @Test
    void claim_logicallyExpiredRecord_replacesItWithFreshClaim() {
        Claim stale = coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE).claimed().orElseThrow();
        repository.expireLogically(stale.scopeKey(), T0.getEpochSecond() - 1);

        clock.advance(Duration.ofSeconds(1));
        ClaimOutcome outcome = coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE);

        assertTrue(outcome.claimed().isPresent());
        assertFalse(outcome.claimed().get().recoveredFromPreviousAttempt(),
                "logical expiry is a fresh claim, not a takeover");
        assertNotEquals(stale.resourceId(), outcome.claimed().get().resourceId());
    }

    @Test
    void completeClaim_lostLease_returnsFalseWithoutOverwritingResult() {
        Claim holder = coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE).claimed().orElseThrow();

        clock.advance(LEASE.multipliedBy(2));
        Claim thief = coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE).claimed().orElseThrow();
        assertTrue(coordinator.completeClaim(thief, ResultSnapshot.jsonBody("{\"id\":\"p-thief\"}"), clock.instant()));

        assertFalse(coordinator.completeClaim(holder, ResultSnapshot.jsonBody("{\"id\":\"p-holder\"}"), clock.instant()));
        assertEquals("{\"id\":\"p-thief\"}",
                repository.findByScopeKey(holder.scopeKey()).orElseThrow().resultSnapshot().body());
    }

    @Test
    void releaseClaim_removesRecordSoImmediateRetryStartsFresh() {
        Claim claim = coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE).claimed().orElseThrow();

        assertTrue(coordinator.releaseClaim(claim, clock.instant()));
        assertTrue(repository.findByScopeKey(claim.scopeKey()).isEmpty());
    }

    @Test
    void releaseClaim_afterTakeover_returnsFalseForStaleHolder() {
        Claim holder = coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE).claimed().orElseThrow();

        clock.advance(LEASE.multipliedBy(2));
        coordinator.claim(scope(), requestHash(), "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, LEASE);

        assertFalse(coordinator.releaseClaim(holder, clock.instant()));
        assertTrue(repository.findByScopeKey(holder.scopeKey()).isPresent());
    }

    @Test
    void retryAfterSecondsFor_unknownRecord_fallsBackToProvidedMinimum() {
        assertEquals(5, coordinator.retryAfterSecondsFor(null, Duration.ofSeconds(5)));
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(final Instant start) {
            this.instant = start;
        }

        void advance(final Duration d) {
            instant = instant.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    /** Records metric calls so tests can assert the protocol surfaced the expected outcomes. */
    private static final class RecordingMetrics implements com.spotpobre.backend.application.idempotency.port.out.IdempotencyMetrics {

        private int claimedNew;
        private int recoveredClaims;
        private int replaysCompleted;
        private int activeLeases;

        @Override
        public void incrementClaimOutcome(final ClaimOutcomeTag outcome) {
            switch (outcome) {
                case CLAIMED_NEW -> claimedNew++;
                case RECOVERED_CLAIM -> recoveredClaims++;
                case REPLAY_COMPLETED -> replaysCompleted++;
                case ACTIVE_LEASE -> activeLeases++;
                default -> { }
            }
        }

        @Override
        public void incrementTransition(final TransitionTag transition) {
            // not asserted in these tests
        }
    }
}
