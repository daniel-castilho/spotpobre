package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.CreateArtistIdempotentlyUseCase.CreateArtistCommand;
import com.spotpobre.backend.application.artist.port.in.CreateArtistIdempotentlyUseCase.CreateArtistOutcome;
import com.spotpobre.backend.application.idempotency.ClaimOutcome;
import com.spotpobre.backend.application.idempotency.IdempotencyCoordinator;
import com.spotpobre.backend.application.idempotency.InMemoryIdempotencyRecordRepository;
import com.spotpobre.backend.application.idempotency.port.out.IdempotencyMetrics;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.common.IdempotencyConflictException;
import com.spotpobre.backend.domain.common.IdempotencyInProgressException;
import com.spotpobre.backend.domain.common.IdempotencyKey;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyScope;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyState;
import com.spotpobre.backend.domain.user.model.Role;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateArtistIdempotentServiceTest {

    private static final Instant T0 = Instant.parse("2026-03-02T09:00:00Z");

    private MutableClock clock;
    private InMemoryIdempotencyRecordRepository idempotencyStore;
    private IdempotencyCoordinator coordinator;
    private ArtistRepository artistRepository;
    private UserRepository userRepository;
    private CreateArtistIdempotentService service;

    private UserId adminId;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        idempotencyStore = new InMemoryIdempotencyRecordRepository();
        coordinator = new IdempotencyCoordinator(idempotencyStore, clock, NoopMetrics.INSTANCE);
        artistRepository = mock(ArtistRepository.class);
        userRepository = mock(UserRepository.class);
        service = new CreateArtistIdempotentService(coordinator, artistRepository, userRepository, clock);

        adminId = UserId.generate();
        when(userRepository.findById(any())).thenAnswer(inv -> Optional.empty());
    }

    private void givenOwnerWithArtistRole(final UUID ownerUserId) {
        User owner = User.builder()
                .id(new UserId(ownerUserId))
                .profile(new UserProfile("Owner", "owner-" + ownerUserId + "@example.com", "BR"))
                .roles(EnumSet.of(Role.USER, Role.ARTIST))
                .build();
        when(userRepository.findById(new UserId(ownerUserId))).thenReturn(Optional.of(owner));
    }

    @Test
    void createArtistIdempotently_newKey_createsArtistUnderReservedIdWithOwnerAccount() {
        UUID ownerId = UUID.randomUUID();
        givenOwnerWithArtistRole(ownerId);
        when(artistRepository.findById(any())).thenReturn(Optional.empty());
        AtomicReference<Artist> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(artistRepository).createWithOwner(any(), any());
        String key = validKey();

        CreateArtistOutcome outcome =
                service.createArtistIdempotently(key, adminId, new CreateArtistCommand("Aurora", ownerId));

        assertFalse(outcome.replayed());
        verify(artistRepository).createWithOwner(any(), any());

        var stored = idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow();
        assertEquals(IdempotencyState.COMPLETED, stored.state());
        assertEquals(outcome.artist().getId().value().toString(), stored.resourceId(),
                "persisted artist must carry the claim-reserved id");
    }

    @Test
    void createArtistIdempotently_ownerMissing_failsBeforeClaimWithoutConsumingKey() {
        UUID unknownOwner = UUID.randomUUID();
        String key = validKey();

        assertThrows(NotFoundException.class,
                () -> service.createArtistIdempotently(key, adminId,
                        new CreateArtistCommand("Aurora", unknownOwner)));

        assertTrue(idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).isEmpty(),
                "deterministic pre-claim validation failures must not consume the key");
        verify(artistRepository, never()).createWithOwner(any(), any());
    }

    @Test
    void createArtistIdempotently_ownerWithoutArtistRole_forbiddenBeforeClaim() {
        UUID plainUserId = UUID.randomUUID();
        User plainUser = User.builder()
                .id(new UserId(plainUserId))
                .profile(new UserProfile("Plain", "plain@example.com", "BR"))
                .roles(EnumSet.of(Role.USER))
                .build();
        when(userRepository.findById(new UserId(plainUserId))).thenReturn(Optional.of(plainUser));

        assertThrows(ForbiddenException.class,
                () -> service.createArtistIdempotently(validKey(), adminId,
                        new CreateArtistCommand("Aurora", plainUserId)));
        verify(artistRepository, never()).createWithOwner(any(), any());
    }

    @Test
    void createArtistIdempotently_sameKeySameRequest_replaysStoredResult() {
        UUID ownerId = UUID.randomUUID();
        givenOwnerWithArtistRole(ownerId);
        String key = validKey();

        // Faithful storage: artist becomes findable only after the write lands.
        AtomicReference<Artist> saved = new AtomicReference<>();
        when(artistRepository.findById(any(ArtistId.class))).thenAnswer(inv -> {
            ArtistId asked = inv.getArgument(0);
            Artist stored = saved.get();
            return stored != null && stored.getId().equals(asked)
                    ? Optional.of(stored) : Optional.empty();
        });
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(artistRepository).createWithOwner(any(), any());

        CreateArtistOutcome first = service.createArtistIdempotently(
                key, adminId, new CreateArtistCommand("Aurora", ownerId));
        CreateArtistOutcome second = service.createArtistIdempotently(
                key, adminId, new CreateArtistCommand("Aurora", ownerId));

        assertFalse(first.replayed());
        assertTrue(second.replayed());
        assertEquals(first.artist().getId(), second.artist().getId());
        verify(artistRepository, times(1)).createWithOwner(any(), any());
    }

    @Test
    void createArtistIdempotently_sameKeyDifferentRequest_returnsKeyReuseConflict() {
        UUID ownerId = UUID.randomUUID();
        givenOwnerWithArtistRole(ownerId);
        when(artistRepository.findById(any())).thenReturn(Optional.empty());
        doAnswer(inv -> null).when(artistRepository).createWithOwner(any(), any());
        String key = validKey();

        service.createArtistIdempotently(key, adminId, new CreateArtistCommand("Aurora", ownerId));

        assertThrows(IdempotencyConflictException.class,
                () -> service.createArtistIdempotently(key, adminId,
                        new CreateArtistCommand("Different name", ownerId)));
    }

    @Test
    void createArtistIdempotently_sameActorOnly_actorScopesTheOperation() {
        UUID ownerId = UUID.randomUUID();
        givenOwnerWithArtistRole(ownerId);
        when(artistRepository.findById(any())).thenReturn(Optional.empty());
        AtomicReference<Artist> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(artistRepository).createWithOwner(any(), any());
        String key = validKey();
        CreateArtistCommand command = new CreateArtistCommand("Aurora", ownerId);

        CreateArtistOutcome first = service.createArtistIdempotently(key, adminId, command);
        CreateArtistOutcome otherAdmin =
                service.createArtistIdempotently(key, UserId.generate(), command);

        assertFalse(first.replayed());
        assertFalse(otherAdmin.replayed(),
                "the same key from a different admin is a different logical operation");
    }

    @Test
    void createArtistIdempotently_activeForeignLease_throwsInProgressWithCappedRetryAfter() {
        UUID ownerId = UUID.randomUUID();
        givenOwnerWithArtistRole(ownerId);
        String key = validKey();
        coordinator.claim(scopeOf(key), requestHash(ownerId), "CreateArtist",
                com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType.ARTIST,
                IdempotencyCoordinator.DEFAULT_CREATION_LEASE);

        IdempotencyInProgressException exception = assertThrows(IdempotencyInProgressException.class,
                () -> service.createArtistIdempotently(key, adminId,
                        new CreateArtistCommand("Aurora", ownerId)));

        assertTrue(exception.getRetryAfterSeconds() >= 1 && exception.getRetryAfterSeconds() <= 30);
    }

    @Test
    void createArtistIdempotently_crashAfterWrite_recoversExistingArtistWithoutRewriting() {
        UUID ownerId = UUID.randomUUID();
        givenOwnerWithArtistRole(ownerId);
        String key = validKey();
        ClaimOutcome crashed = coordinator.claim(scopeOf(key), requestHash(ownerId), "CreateArtist",
                com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType.ARTIST,
                IdempotencyCoordinator.DEFAULT_CREATION_LEASE);
        clock.advance(IdempotencyCoordinator.DEFAULT_CREATION_LEASE.multipliedBy(2));

        ArtistId reservedId = ArtistId.from(crashed.claimed().orElseThrow().resourceId());
        Artist writtenBeforeCrash = Artist.create(reservedId, "Aurora");
        when(artistRepository.findById(reservedId)).thenReturn(Optional.of(writtenBeforeCrash));

        CreateArtistOutcome outcome =
                service.createArtistIdempotently(key, adminId, new CreateArtistCommand("Aurora", ownerId));

        assertFalse(outcome.replayed(), "recovery executes once more to reach completion");
        assertSame(writtenBeforeCrash, outcome.artist());
        verify(artistRepository, never()).createWithOwner(any(), any());
        assertEquals(IdempotencyState.COMPLETED,
                idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow().state());
    }

    @Test
    void createArtistIdempotently_unexpectedFailureAfterClaim_retainsInProgressForRecovery() {
        UUID ownerId = UUID.randomUUID();
        givenOwnerWithArtistRole(ownerId);
        String key = validKey();
        when(artistRepository.findById(any()))
                .thenThrow(new IllegalStateException("DynamoDB down"));

        assertThrows(IllegalStateException.class,
                () -> service.createArtistIdempotently(key, adminId,
                        new CreateArtistCommand("Aurora", ownerId)));

        assertEquals(IdempotencyState.IN_PROGRESS,
                idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow().state());
    }

    private static String validKey() {
        return "artist-it-" + UUID.randomUUID();
    }

    private IdempotencyScope scopeOf(final String rawKey) {
        return new IdempotencyScope(CreateArtistIdempotentService.API_VERSION, "user:" + adminId.value(),
                "POST", CreateArtistIdempotentService.ROUTE_TEMPLATE, "", IdempotencyKey.of(rawKey));
    }

    private static CanonicalRequestHash requestHash(final UUID ownerId) {
        return CanonicalRequestHash.current(List.of("Aurora", String.valueOf(ownerId)));
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
