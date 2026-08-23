package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.idempotency.ClaimOutcome;
import com.spotpobre.backend.application.idempotency.IdempotencyCoordinator;
import com.spotpobre.backend.application.idempotency.InMemoryIdempotencyRecordRepository;
import com.spotpobre.backend.application.idempotency.port.out.IdempotencyMetrics;
import com.spotpobre.backend.domain.common.ConflictException;
import com.spotpobre.backend.domain.common.IdempotencyConflictException;
import com.spotpobre.backend.domain.common.IdempotencyInProgressException;
import com.spotpobre.backend.domain.common.IdempotencyKey;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyScope;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyState;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreatePlaylistIdempotentServiceTest {

    private static final Instant T0 = Instant.parse("2026-03-02T09:00:00Z");
    private static final long MAX_PLAYLISTS = 10;

    private MutableClock clock;
    private InMemoryIdempotencyRecordRepository idempotencyStore;
    private IdempotencyCoordinator coordinator;
    private UserRepository userRepository;
    private PlaylistRepository playlistRepository;
    private CreatePlaylistIdempotentService service;

    private final UserId ownerId = UserId.generate();

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        idempotencyStore = new InMemoryIdempotencyRecordRepository();
        coordinator = new IdempotencyCoordinator(idempotencyStore, clock, NoopMetrics.INSTANCE);
        userRepository = mock(UserRepository.class);
        playlistRepository = mock(PlaylistRepository.class);
        service = new CreatePlaylistIdempotentService(coordinator, userRepository, playlistRepository, clock);

        when(userRepository.findById(any())).thenAnswer(inv ->
                Optional.of(User.builder()
                        .id(inv.getArgument(0))
                        .profile(new UserProfile("Owner", "owner@example.com", "BR"))
                        .roles(EnumSet.of(com.spotpobre.backend.domain.user.model.Role.USER))
                        .build()));
    }

    @Test
    void createPlaylistIdempotently_newKey_createsPlaylistUnderReservedId() {
        String key = validKey();
        when(playlistRepository.findById(any())).thenReturn(Optional.empty());
        AtomicReference<Playlist> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(playlistRepository).createWithinOwnerLimit(any(), anyInt());

        var outcome = service.createPlaylistIdempotently(key, ownerId.value(), "Road Trip");

        assertFalse(outcome.replayed());
        verify(playlistRepository).createWithinOwnerLimit(any(), anyInt());

        var stored = idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow();
        assertEquals(IdempotencyState.COMPLETED, stored.state());
        assertEquals(outcome.playlist().getId().value().toString(), stored.resourceId(),
                "persisted playlist must carry the claim-reserved id");
    }

    @Test
    void createPlaylistIdempotently_unknownUser_failsBeforeClaimWithoutConsumingKey() {
        String key = validKey();
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.createPlaylistIdempotently(key, UUID.randomUUID(), "Road Trip"));

        assertTrue(idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).isEmpty(),
                "deterministic pre-claim validation failures must not consume the key");
        verify(playlistRepository, never()).createWithinOwnerLimit(any(), anyInt());
    }

    @Test
    void createPlaylistIdempotently_sameKeySameRequest_replaysStoredResult() {
        String key = validKey();
        AtomicReference<Playlist> saved = new AtomicReference<>();
        when(playlistRepository.findById(any(PlaylistId.class))).thenAnswer(inv -> {
            PlaylistId asked = inv.getArgument(0);
            Playlist stored = saved.get();
            return stored != null && stored.getId().equals(asked)
                    ? Optional.of(stored) : Optional.empty();
        });
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(playlistRepository).createWithinOwnerLimit(any(), anyInt());

        var first = service.createPlaylistIdempotently(key, ownerId.value(), "Road Trip");
        var second = service.createPlaylistIdempotently(key, ownerId.value(), "Road Trip");

        assertFalse(first.replayed());
        assertTrue(second.replayed());
        assertEquals(first.playlist().getId(), second.playlist().getId());
        verify(playlistRepository, times(1)).createWithinOwnerLimit(any(), anyInt());
    }

    @Test
    void createPlaylistIdempotently_sameKeyDifferentRequest_returnsKeyReuseConflict() {
        String key = validKey();
        when(playlistRepository.findById(any())).thenReturn(Optional.empty());
        doAnswer(inv -> null).when(playlistRepository).createWithinOwnerLimit(any(), anyInt());

        service.createPlaylistIdempotently(key, ownerId.value(), "Road Trip");

        assertThrows(IdempotencyConflictException.class,
                () -> service.createPlaylistIdempotently(key, ownerId.value(), "Different Name"));
    }

    @Test
    void createPlaylistIdempotently_activeForeignLease_throwsInProgressWithCappedRetryAfter() {
        String key = validKey();
        coordinator.claim(scopeOf(key), requestHash("Road Trip"), "CreatePlaylist",
                com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType.PLAYLIST,
                IdempotencyCoordinator.DEFAULT_CREATION_LEASE);

        IdempotencyInProgressException exception = assertThrows(IdempotencyInProgressException.class,
                () -> service.createPlaylistIdempotently(key, ownerId.value(), "Road Trip"));

        assertTrue(exception.getRetryAfterSeconds() >= 1 && exception.getRetryAfterSeconds() <= 30);
    }

    @Test
    void createPlaylistIdempotently_limitReachedAtExecution_failsClaimFinalWith409() {
        String key = validKey();
        when(playlistRepository.findById(any())).thenReturn(Optional.empty());
        // The storage-level transaction refuses the write when the limit would be exceeded
        doThrow(new ConflictException("User cannot have more than " + MAX_PLAYLISTS + " playlists."))
                .when(playlistRepository).createWithinOwnerLimit(any(), anyInt());

        assertThrows(ConflictException.class,
                () -> service.createPlaylistIdempotently(key, ownerId.value(), "Road Trip"));

        assertEquals(IdempotencyState.FAILED_FINAL,
                idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow().state());

        // Retry replays the deterministic 409 instead of re-executing: the storage-level write
        // was attempted exactly once (first attempt, rejected) and never on replay.
        assertThrows(ConflictException.class,
                () -> service.createPlaylistIdempotently(key, ownerId.value(), "Road Trip"));
        verify(playlistRepository, times(1)).createWithinOwnerLimit(any(), anyInt());
    }

    @Test
    void createPlaylistIdempotently_crashAfterWrite_recoversExistingPlaylistWithoutRewriting() {
        String key = validKey();
        ClaimOutcome crashed = coordinator.claim(scopeOf(key), requestHash("Road Trip"), "CreatePlaylist",
                com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType.PLAYLIST,
                IdempotencyCoordinator.DEFAULT_CREATION_LEASE);
        clock.advance(IdempotencyCoordinator.DEFAULT_CREATION_LEASE.multipliedBy(2));

        PlaylistId reservedId = PlaylistId.from(crashed.claimed().orElseThrow().resourceId());
        Playlist writtenBeforeCrash = Playlist.create(reservedId, "Road Trip", ownerId);
        when(playlistRepository.findById(reservedId)).thenReturn(Optional.of(writtenBeforeCrash));

        var outcome = service.createPlaylistIdempotently(key, ownerId.value(), "Road Trip");

        assertFalse(outcome.replayed(), "recovery executes once more to reach completion");
        assertSame(writtenBeforeCrash, outcome.playlist());
        verify(playlistRepository, never()).createWithinOwnerLimit(any(), anyInt());
        assertEquals(IdempotencyState.COMPLETED,
                idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow().state());
    }

    @Test
    void createPlaylistIdempotently_unexpectedFailureAfterClaim_retainsInProgressForRecovery() {
        String key = validKey();
        when(playlistRepository.findById(any()))
                .thenThrow(new IllegalStateException("DynamoDB down"));

        assertThrows(IllegalStateException.class,
                () -> service.createPlaylistIdempotently(key, ownerId.value(), "Road Trip"));

        assertEquals(IdempotencyState.IN_PROGRESS,
                idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow().state());
    }

    private static String validKey() {
        return "playlist-it-" + UUID.randomUUID();
    }

    private IdempotencyScope scopeOf(final String rawKey) {
        return new IdempotencyScope(CreatePlaylistIdempotentService.API_VERSION, "user:" + ownerId.value(),
                "POST", CreatePlaylistIdempotentService.ROUTE_TEMPLATE, "", IdempotencyKey.of(rawKey));
    }

    private static CanonicalRequestHash requestHash(final String name) {
        return CanonicalRequestHash.current(java.util.List.of(name));
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

    private enum NoopMetrics implements IdempotencyMetrics {
        INSTANCE;

        @Override
        public void incrementClaimOutcome(final ClaimOutcomeTag outcome) {
        }

        @Override
        public void incrementTransition(final TransitionTag transition) {
        }
    }
}
