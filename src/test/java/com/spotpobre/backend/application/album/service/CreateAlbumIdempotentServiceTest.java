package com.spotpobre.backend.application.album.service;

import com.spotpobre.backend.application.album.port.in.CreateAlbumIdempotentlyUseCase.CreateAlbumCommand;
import com.spotpobre.backend.application.album.port.in.CreateAlbumIdempotentlyUseCase.CreateAlbumOutcome;
import com.spotpobre.backend.application.artist.port.in.RequireArtistAccessUseCase;
import com.spotpobre.backend.application.idempotency.ClaimOutcome;
import com.spotpobre.backend.application.idempotency.IdempotencyCoordinator;
import com.spotpobre.backend.application.idempotency.InMemoryIdempotencyRecordRepository;
import com.spotpobre.backend.application.idempotency.port.out.IdempotencyMetrics;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.common.IdempotencyConflictException;
import com.spotpobre.backend.domain.common.IdempotencyInProgressException;
import com.spotpobre.backend.domain.common.IdempotencyKey;
import com.spotpobre.backend.domain.common.IdempotencyLeaseLostException;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyScope;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateAlbumIdempotentServiceTest {

    private static final Instant T0 = Instant.parse("2026-03-02T09:00:00Z");

    private MutableClock clock;
    private InMemoryIdempotencyRecordRepository idempotencyStore;
    private IdempotencyCoordinator coordinator;
    private ArtistRepository artistRepository;
    private AlbumRepository albumRepository;
    private RequireArtistAccessUseCase requireArtistAccess;
    private CreateAlbumIdempotentService service;

    private ArtistId artistId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        idempotencyStore = new InMemoryIdempotencyRecordRepository();
        coordinator = new IdempotencyCoordinator(idempotencyStore, clock, NoopMetrics.INSTANCE);
        artistRepository = mock(ArtistRepository.class);
        albumRepository = mock(AlbumRepository.class);
        requireArtistAccess = mock(RequireArtistAccessUseCase.class);
        service = new CreateAlbumIdempotentService(coordinator, artistRepository, albumRepository,
                requireArtistAccess, clock);

        artistId = new ArtistId(UUID.randomUUID());
        actorId = UUID.randomUUID();
        when(artistRepository.findById(any())).thenReturn(Optional.of(Artist.create(artistId, "Aurora")));
    }

    private CreateAlbumCommand command(String name) {
        return new CreateAlbumCommand(name, artistId, null, actorId, false);
    }

    @Test
    void createAlbumIdempotently_newKey_createsAlbumUnderReservedId() {
        String key = validKey();
        when(albumRepository.findById(any())).thenReturn(Optional.empty());
        AtomicReference<Album> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(albumRepository).save(any());

        CreateAlbumOutcome outcome =
                service.createAlbumIdempotently(key, command("Nightfall"));

        assertFalse(outcome.replayed());
        verify(albumRepository).save(any());

        var stored = idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow();
        assertEquals(IdempotencyState.COMPLETED, stored.state());
        assertEquals(outcome.album().getId().value().toString(), stored.resourceId(),
                "persisted album must carry the claim-reserved id");
    }

    @Test
    void createAlbumIdempotently_lostLeaseBeforePublish_throwsAndKeepsRecordInProgress() {
        when(albumRepository.findById(any())).thenReturn(Optional.empty());

        String key = validKey();
        idempotencyStore.failNextConditionalTransition.set(true);

        assertThrows(IdempotencyLeaseLostException.class,
                () -> service.createAlbumIdempotently(key, command("Nightfall")));

        var lostLeaseRecord = idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow();
        assertEquals(IdempotencyState.IN_PROGRESS, lostLeaseRecord.state(),
                "a lost lease must not publish a COMPLETED record");
    }

    @Test
    void createAlbumIdempotently_unknownArtist_failsBeforeClaimWithoutConsumingKey() {
        ArtistId unknown = new ArtistId(UUID.randomUUID());
        when(artistRepository.findById(unknown)).thenReturn(Optional.empty());
        String key = validKey();

        assertThrows(NotFoundException.class,
                () -> service.createAlbumIdempotently(key,
                        new CreateAlbumCommand("Nightfall", unknown, null, UUID.randomUUID(), false)));

        assertTrue(idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).isEmpty(),
                "deterministic pre-claim validation failures must not consume the key");
        verify(albumRepository, never()).save(any());
    }

    @Test
    void createAlbumIdempotently_nonMemberActor_forbiddenBeforeClaim() {
        doThrow(new ForbiddenException("No membership on artist"))
                .when(requireArtistAccess).requireAccess(any(), any());

        assertThrows(ForbiddenException.class,
                () -> service.createAlbumIdempotently(validKey(), command("Nightfall")));
        verify(albumRepository, never()).save(any());
    }

    @Test
    void createAlbumIdempotently_sameKeySameRequest_replaysStoredResult() {
        String key = validKey();
        AtomicReference<Album> saved = new AtomicReference<>();
        when(albumRepository.findById(any(AlbumId.class))).thenAnswer(inv -> {
            AlbumId asked = inv.getArgument(0);
            Album stored = saved.get();
            return stored != null && stored.getId().equals(asked)
                    ? Optional.of(stored) : Optional.empty();
        });
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(albumRepository).save(any());

        CreateAlbumOutcome first = service.createAlbumIdempotently(key, command("Nightfall"));
        CreateAlbumOutcome second = service.createAlbumIdempotently(key, command("Nightfall"));

        assertFalse(first.replayed());
        assertTrue(second.replayed());
        assertEquals(first.album().getId(), second.album().getId());
        verify(albumRepository, times(1)).save(any());
    }

    @Test
    void createAlbumIdempotently_sameKeyDifferentRequest_returnsKeyReuseConflict() {
        String key = validKey();
        when(albumRepository.findById(any())).thenReturn(Optional.empty());
        doAnswer(inv -> null).when(albumRepository).save(any());

        service.createAlbumIdempotently(key, command("Nightfall"));

        assertThrows(IdempotencyConflictException.class,
                () -> service.createAlbumIdempotently(key, command("Different name")));
    }

    @Test
    void createAlbumIdempotently_activeForeignLease_throwsInProgressWithCappedRetryAfter() {
        String key = validKey();
        coordinator.claim(scopeOf(key), requestHash(), "CreateAlbum",
                com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType.ALBUM,
                IdempotencyCoordinator.DEFAULT_CREATION_LEASE);

        IdempotencyInProgressException exception = assertThrows(IdempotencyInProgressException.class,
                () -> service.createAlbumIdempotently(key, command("Nightfall")));

        assertTrue(exception.getRetryAfterSeconds() >= 1 && exception.getRetryAfterSeconds() <= 30);
    }

    @Test
    void createAlbumIdempotently_crashAfterWrite_recoversExistingAlbumWithoutRewriting() {
        String key = validKey();
        ClaimOutcome crashed = coordinator.claim(scopeOf(key), requestHash(), "CreateAlbum",
                com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType.ALBUM,
                IdempotencyCoordinator.DEFAULT_CREATION_LEASE);
        clock.advance(IdempotencyCoordinator.DEFAULT_CREATION_LEASE.multipliedBy(2));

        AlbumId reservedId = AlbumId.from(crashed.claimed().orElseThrow().resourceId());
        Album writtenBeforeCrash = Album.builder()
                .id(reservedId).name("Nightfall").artistId(artistId).build();
        when(albumRepository.findById(reservedId)).thenReturn(Optional.of(writtenBeforeCrash));

        CreateAlbumOutcome outcome = service.createAlbumIdempotently(key, command("Nightfall"));

        assertFalse(outcome.replayed(), "recovery executes once more to reach completion");
        assertSame(writtenBeforeCrash, outcome.album());
        verify(albumRepository, never()).save(any());
        assertEquals(IdempotencyState.COMPLETED,
                idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow().state());
    }

    @Test
    void createAlbumIdempotently_unexpectedFailureAfterClaim_retainsInProgressForRecovery() {
        String key = validKey();
        when(albumRepository.findById(any()))
                .thenThrow(new IllegalStateException("DynamoDB down"));

        assertThrows(IllegalStateException.class,
                () -> service.createAlbumIdempotently(key, command("Nightfall")));

        assertEquals(IdempotencyState.IN_PROGRESS,
                idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow().state());
    }

    private static String validKey() {
        return "album-it-" + UUID.randomUUID();
    }

    private IdempotencyScope scopeOf(final String rawKey) {
        return new IdempotencyScope(CreateAlbumIdempotentService.API_VERSION, "user:" + actorId,
                "POST", CreateAlbumIdempotentService.ROUTE_TEMPLATE, "", IdempotencyKey.of(rawKey));
    }

    private CanonicalRequestHash requestHash() {
        return CanonicalRequestHash.current(List.of("Nightfall", artistId.value().toString(), ""));
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
