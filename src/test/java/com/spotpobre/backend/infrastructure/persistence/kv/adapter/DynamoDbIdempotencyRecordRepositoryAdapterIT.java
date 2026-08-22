package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.AbstractIntegrationTest;
import com.spotpobre.backend.domain.common.IdempotencyKey;
import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.FailureDescriptor;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyRecord;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyScope;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyState;
import com.spotpobre.backend.domain.idempotency.model.LeaseToken;
import com.spotpobre.backend.domain.idempotency.model.ResultSnapshot;
import com.spotpobre.backend.domain.idempotency.port.IdempotencyRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DynamoDbIdempotencyRecordRepositoryAdapterIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-02-01T12:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(30);

    @Autowired
    private IdempotencyRecordRepository repository;

    private static IdempotencyScope scope(final String key) {
        return new IdempotencyScope(
                "v1", "user:" + UUID.randomUUID(), "POST", "/api/v1/playlists", "",
                IdempotencyKey.of(key));
    }

    private static CanonicalRequestHash requestHash() {
        return CanonicalRequestHash.current(List.of("name=Road trip", "ownerId=u-1"));
    }

    private static IdempotencyRecord inProgressClaim(final IdempotencyScope scope, final LeaseToken lease,
                                                     final Instant now) {
        return IdempotencyRecord.builder()
                .scopeKey(scope.scopeKey())
                .operationName("CreatePlaylist")
                .routeTemplate(scope.routeTemplate())
                .actorScopeHash("actor-hash-" + UUID.randomUUID())
                .requestHash(requestHash())
                .state(IdempotencyState.IN_PROGRESS)
                .resourceType(IdempotencyResourceType.PLAYLIST)
                .resourceId(UUID.randomUUID().toString())
                .lease(lease)
                .leaseUntil(now.plus(LEASE))
                .createdAt(now)
                .updatedAt(now)
                .expiresAtEpochSeconds(now.plus(Duration.ofHours(24)).getEpochSecond())
                .build();
    }

    @Test
    void insertInProgress_persistsRecordAndRoundTripsAllSafeFields() {
        IdempotencyScope scope = scope("roundtrip-" + UUID.randomUUID());
        IdempotencyRecord record = inProgressClaim(scope, LeaseToken.generate(), NOW);

        assertTrue(repository.insertInProgress(record));

        Optional<IdempotencyRecord> found = repository.findByScopeKey(scope.scopeKey());
        assertTrue(found.isPresent());
        IdempotencyRecord stored = found.get();
        assertEquals(record.scopeKey(), stored.scopeKey());
        assertEquals(record.operationName(), stored.operationName());
        assertEquals(record.routeTemplate(), stored.routeTemplate());
        assertEquals(record.requestHash(), stored.requestHash());
        assertEquals(record.state(), stored.state());
        assertEquals(record.resourceType(), stored.resourceType());
        assertEquals(record.resourceId(), stored.resourceId());
        assertEquals(record.lease().hash(), stored.lease().hash());
        assertEquals(record.leaseUntil(), stored.leaseUntil());
        assertEquals(record.expiresAtEpochSeconds(), stored.expiresAtEpochSeconds());
        assertEquals(64, stored.lease().hash().length(), "only the SHA-256 hex hash is persisted");
    }

    @Test
    void insertInProgress_duplicateScope_failsConditionally() {
        IdempotencyScope scope = scope("dup-" + UUID.randomUUID());

        assertTrue(repository.insertInProgress(inProgressClaim(scope, LeaseToken.generate(), NOW)));
        assertFalse(repository.insertInProgress(inProgressClaim(scope, LeaseToken.generate(), NOW)));
    }

    @Test
    void markCompleted_withHeldLease_becomesDurableAndReplayable() {
        IdempotencyScope scope = scope("complete-" + UUID.randomUUID());
        LeaseToken lease = LeaseToken.generate();
        assertTrue(repository.insertInProgress(inProgressClaim(scope, lease, NOW)));

        ResultSnapshot snapshot = ResultSnapshot.jsonBody("{\"id\":\"p-it-1\"}");
        assertTrue(repository.markCompleted(scope.scopeKey(), lease, snapshot, NOW, NOW));

        Optional<IdempotencyRecord> found = repository.findByScopeKey(scope.scopeKey());
        assertTrue(found.isPresent());
        assertEquals(IdempotencyState.COMPLETED, found.get().state());
        assertEquals("{\"id\":\"p-it-1\"}", found.get().resultSnapshot().body());
        assertEquals(NOW, found.get().completedAt());
    }

    @Test
    void markCompleted_withForeignLease_failsWithoutOverwriting() {
        IdempotencyScope scope = scope("foreign-" + UUID.randomUUID());
        assertTrue(repository.insertInProgress(inProgressClaim(scope, LeaseToken.generate(), NOW)));

        assertFalse(repository.markCompleted(scope.scopeKey(), LeaseToken.generate(),
                ResultSnapshot.jsonBody("{\"id\":\"impostor\"}"), NOW, NOW));
        assertEquals(IdempotencyState.IN_PROGRESS, repository.findByScopeKey(scope.scopeKey()).orElseThrow().state());
    }

    @Test
    void takeoverExpiredLease_preservesResourceIdAndSwapsLeaseOnlyAfterExpiry() {
        IdempotencyScope scope = scope("takeover-" + UUID.randomUUID());
        IdempotencyRecord original = inProgressClaim(scope, LeaseToken.generate(), NOW);
        assertTrue(repository.insertInProgress(original));

        // Still leased: no takeover.
        LeaseToken earlyLease = LeaseToken.generate();
        Instant earlyNow = NOW.plus(Duration.ofSeconds(5));
        assertFalse(repository.takeoverExpiredLease(scope.scopeKey(), requestHash(),
                earlyLease, earlyNow.plus(LEASE), earlyNow));

        // Expired: takeover succeeds and preserves the reserved resource ID.
        LeaseToken newLease = LeaseToken.generate();
        Instant laterNow = NOW.plus(LEASE.multipliedBy(2));
        assertTrue(repository.takeoverExpiredLease(scope.scopeKey(), requestHash(),
                newLease, laterNow.plus(LEASE), laterNow));

        Optional<IdempotencyRecord> found = repository.findByScopeKey(scope.scopeKey());
        assertTrue(found.isPresent());
        assertEquals(original.resourceId(), found.get().resourceId());
        assertEquals(newLease.hash(), found.get().lease().hash());
        assertEquals(laterNow.plus(LEASE), found.get().leaseUntil());
        assertEquals(IdempotencyState.IN_PROGRESS, found.get().state());
    }

    @Test
    void replaceLogicallyExpired_replacesOnlyWhenTtlReached() {
        IdempotencyScope scope = scope("expired-" + UUID.randomUUID());
        IdempotencyRecord notYetExpired =
                inProgressClaim(scope, LeaseToken.generate(), NOW);
        assertTrue(repository.insertInProgress(notYetExpired));

        // TTL still in the future: replacement must fail.
        IdempotencyRecord prematureReplacement =
                inProgressClaim(scope, LeaseToken.generate(), NOW.plusSeconds(60));
        assertFalse(repository.replaceLogicallyExpired(scope.scopeKey(), prematureReplacement,
                NOW.plusSeconds(120)));
        assertEquals(notYetExpired.resourceId(),
                repository.findByScopeKey(scope.scopeKey()).orElseThrow().resourceId());

        // Record whose whole-record TTL is already in the past (simulating logical expiry ahead
        // of DynamoDB's eventual physical deletion): replacement succeeds with a fresh resource.
        IdempotencyScope lapsedScope = scope("lapsed-" + UUID.randomUUID());
        IdempotencyRecord lapsed = IdempotencyRecord.builder()
                .merge(inProgressClaim(lapsedScope, LeaseToken.generate(), NOW))
                .expiresAtEpochSeconds(NOW.getEpochSecond() - 1)
                .build();
        assertTrue(repository.insertInProgress(lapsed));

        IdempotencyRecord fresh = inProgressClaim(lapsedScope, LeaseToken.generate(), NOW.plusSeconds(3600));
        assertTrue(repository.replaceLogicallyExpired(lapsedScope.scopeKey(), fresh, NOW));
        assertEquals(fresh.resourceId(),
                repository.findByScopeKey(lapsedScope.scopeKey()).orElseThrow().resourceId());
        assertNotEquals(lapsed.resourceId(), fresh.resourceId());
    }

    @Test
    void releaseInProgress_deletesOnlyWithMatchingLease() {
        IdempotencyScope scope = scope("release-" + UUID.randomUUID());
        LeaseToken lease = LeaseToken.generate();
        assertTrue(repository.insertInProgress(inProgressClaim(scope, lease, NOW)));

        assertFalse(repository.releaseInProgress(scope.scopeKey(), LeaseToken.generate()));
        assertTrue(repository.findByScopeKey(scope.scopeKey()).isPresent());

        assertTrue(repository.releaseInProgress(scope.scopeKey(), lease));
        assertTrue(repository.findByScopeKey(scope.scopeKey()).isEmpty());
    }

    @Test
    void markFailedFinal_recordsDeterministicFailureReplayably() {
        IdempotencyScope scope = scope("failed-" + UUID.randomUUID());
        LeaseToken lease = LeaseToken.generate();
        assertTrue(repository.insertInProgress(inProgressClaim(scope, lease, NOW)));

        assertTrue(repository.markFailedFinal(scope.scopeKey(), lease,
                FailureDescriptor.of(409, "PLAYLIST_LIMIT_REACHED", "limit"), NOW));

        Optional<IdempotencyRecord> found = repository.findByScopeKey(scope.scopeKey());
        assertTrue(found.isPresent());
        assertEquals(IdempotencyState.FAILED_FINAL, found.get().state());
        assertEquals(409, found.get().failure().status());
    }

    @Test
    void insertInProgress_concurrentClaimsForSameScope_singleWinner() throws Exception {
        IdempotencyScope scope = scope("race-" + UUID.randomUUID());
        int claimants = 8;
        ExecutorService pool = Executors.newFixedThreadPool(claimants);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < claimants; i++) {
                results.add(pool.submit((Callable<Boolean>) () ->
                        repository.insertInProgress(inProgressClaim(scope, LeaseToken.generate(), NOW))));
            }
            int winners = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    winners++;
                }
            }
            assertEquals(1, winners, "exactly one claimant may win the conditional insert");
        } finally {
            pool.shutdownNow();
        }
    }
}
