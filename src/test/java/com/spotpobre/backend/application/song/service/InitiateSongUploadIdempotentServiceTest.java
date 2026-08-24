package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.artist.port.in.RequireArtistAccessUseCase;
import com.spotpobre.backend.application.idempotency.ClaimOutcome;
import com.spotpobre.backend.application.idempotency.IdempotencyCoordinator;
import com.spotpobre.backend.application.idempotency.InMemoryIdempotencyRecordRepository;
import com.spotpobre.backend.application.idempotency.port.out.IdempotencyMetrics;
import com.spotpobre.backend.application.song.port.in.InitiateSongUploadIdempotentlyUseCase.InitiateSongUploadCommand;
import com.spotpobre.backend.application.song.port.in.InitiateSongUploadIdempotentlyUseCase.InitiateUploadIdempotentResult;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.common.IdempotencyConflictException;
import com.spotpobre.backend.domain.common.IdempotencyInProgressException;
import com.spotpobre.backend.domain.common.IdempotencyKey;
import com.spotpobre.backend.domain.common.IdempotencyLeaseLostException;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyScope;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyState;
import com.spotpobre.backend.domain.song.model.PresignedUploadPart;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.model.SongUpload;
import com.spotpobre.backend.domain.song.model.SongUploadCommand;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import com.spotpobre.backend.domain.song.port.SongUploadRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitiateSongUploadIdempotentServiceTest {

    private static final Instant T0 = Instant.parse("2026-03-02T09:00:00Z");

    private MutableClock clock;
    private InMemoryIdempotencyRecordRepository idempotencyStore;
    private IdempotencyCoordinator coordinator;
    private SongStoragePort songStoragePort;
    private SongUploadRepository songUploadRepository;
    private AlbumRepository albumRepository;
    private RequireArtistAccessUseCase requireArtistAccess;
    private InitiateSongUploadIdempotentService service;

    private final AlbumId albumId = new AlbumId(UUID.randomUUID());
    private final UUID actorId = UUID.randomUUID();
    private final AtomicReference<SongUpload> stagedRecord = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        idempotencyStore = new InMemoryIdempotencyRecordRepository();
        coordinator = new IdempotencyCoordinator(idempotencyStore, clock, NoopMetrics.INSTANCE);
        songStoragePort = mock(SongStoragePort.class);
        songUploadRepository = mock(SongUploadRepository.class);
        albumRepository = mock(AlbumRepository.class);
        requireArtistAccess = mock(RequireArtistAccessUseCase.class);
        service = new InitiateSongUploadIdempotentService(coordinator, songStoragePort,
                songUploadRepository, albumRepository, requireArtistAccess, clock);

        Album album = Album.builder().id(albumId)
                .name("Album").artistId(new ArtistId(UUID.randomUUID())).build();
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(album));
        when(songUploadRepository.findBySongId(any())).thenAnswer(inv ->
                Optional.ofNullable(stagedRecord.get())
                        .filter(r -> r.getSongId().equals(inv.getArgument(0))));
        when(songUploadRepository.insertIfAbsent(any())).thenAnswer(inv -> {
            stagedRecord.set(inv.getArgument(0));
            return true;
        });
        when(songStoragePort.regenerateUploadUrl(anyString(), any()))
                .thenAnswer(inv -> singlePartResult(inv.getArgument(0)));
    }

    private static PresignedUploadResult singlePartResult(final String storageKey) {
        return new PresignedUploadResult(storageKey, null,
                T0.plus(Duration.ofMinutes(10)), false,
                List.of(new PresignedUploadPart(1, "https://presigned/" + storageKey)));
    }

    private InitiateSongUploadCommand command(final String title) {
        return new InitiateSongUploadCommand(title, albumId, "audio/mpeg", 1_000_000L,
                actorId, false);
    }

    @Test
    void initiateUploadIdempotently_newKey_stagesUploadUnderReservedIdWithoutSongRow() {
        String key = validKey();

        InitiateUploadIdempotentResult result =
                service.initiateUploadIdempotently(key, command("Track One"));

        assertFalse(result.replayed());
        assertEquals("pending/" + result.upload().getSongId().value(),
                result.upload().getStagingKey(), "staging key must be server-derived");
        assertEquals(result.upload().getStagingKey(), result.presigned().storageKey(),
                "presigned upload must target the staging key");
        verify(songUploadRepository).insertIfAbsent(any(SongUpload.class));

        var stored = idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow();
        assertEquals(IdempotencyState.COMPLETED, stored.state());
        assertEquals(result.upload().getSongId().value().toString(), stored.resourceId());
    }

    @Test
    void initiateUploadIdempotently_lostLeaseBeforePublish_throwsAndKeepsRecordInProgress() {
        String key = validKey();
        idempotencyStore.failNextConditionalTransition.set(true);

        assertThrows(IdempotencyLeaseLostException.class,
                () -> service.initiateUploadIdempotently(key, command("Track One")));

        var lostLeaseRecord = idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow();
        assertEquals(IdempotencyState.IN_PROGRESS, lostLeaseRecord.state(),
                "a lost lease must not publish a COMPLETED record");
    }

    @Test
    void initiateUploadIdempotently_unknownAlbum_failsBeforeClaimWithoutConsumingKey() {
        when(albumRepository.findById(albumId)).thenReturn(Optional.empty());
        String key = validKey();

        assertThrows(NotFoundException.class, () -> service.initiateUploadIdempotently(key, command("T")));

        assertTrue(idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).isEmpty());
        verify(songUploadRepository, never()).insertIfAbsent(any());
    }

    @Test
    void initiateUploadIdempotently_nonMemberActor_forbiddenBeforeClaim() {
        doThrow(new ForbiddenException("No membership"))
                .when(requireArtistAccess).requireAccess(any(), any());

        assertThrows(ForbiddenException.class,
                () -> service.initiateUploadIdempotently(validKey(), command("T")));
        verify(songUploadRepository, never()).insertIfAbsent(any());
    }

    @Test
    void initiateUploadIdempotently_sameKeySameRequest_replaysStagedRecordWithFreshUrl() {
        String key = validKey();

        InitiateUploadIdempotentResult first =
                service.initiateUploadIdempotently(key, command("Track One"));
        InitiateUploadIdempotentResult second =
                service.initiateUploadIdempotently(key, command("Track One"));

        assertFalse(first.replayed());
        assertTrue(second.replayed());
        assertEquals(first.upload().getSongId(), second.upload().getSongId());
        assertEquals(second.upload().getStagingKey(), second.presigned().storageKey(),
                "replay must re-presign for the same staging key");
        verify(songStoragePort, never()).generateUploadUrl(any());
        verify(songStoragePort, times(2)).regenerateUploadUrl(anyString(), any());
    }

    @Test
    void initiateUploadIdempotently_sameKeyDifferentRequest_returnsKeyReuseConflict() {
        String key = validKey();

        service.initiateUploadIdempotently(key, command("Track One"));

        assertThrows(IdempotencyConflictException.class,
                () -> service.initiateUploadIdempotently(key, command("Different Track")));
    }

    @Test
    void initiateUploadIdempotently_activeForeignLease_throwsInProgressWithCappedRetryAfter() {
        String key = validKey();
        coordinator.claim(scopeOf(key), requestHash("Track One"), "InitiateSongUpload",
                IdempotencyResourceType.SONG_UPLOAD, IdempotencyCoordinator.UPLOAD_LEASE);

        InitiateSongUploadCommand cmd = command("Track One");
        // The direct probe above used a hash built from the same inputs; align it.
        var record = idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow();

        InitiateSongUploadCommand finalCmd = cmd;
        IdempotencyInProgressException exception = assertThrows(IdempotencyInProgressException.class,
                () -> service.initiateUploadIdempotently(key, finalCmd));

        assertTrue(exception.getRetryAfterSeconds() >= 1 && exception.getRetryAfterSeconds() <= 30);
        assertEquals(IdempotencyState.IN_PROGRESS, record.state());
    }

    @Test
    void initiateUploadIdempotently_crashAfterInsert_recoversExistingStagedRecordAndRegeneratesUrl() {
        String key = validKey();
        ClaimOutcome crashed = coordinator.claim(scopeOf(key), requestHash("Track One"),
                "InitiateSongUpload", IdempotencyResourceType.SONG_UPLOAD,
                IdempotencyCoordinator.UPLOAD_LEASE);
        clock.advance(IdempotencyCoordinator.UPLOAD_LEASE.multipliedBy(2));

        SongId reserved = SongId.from(crashed.claimed().orElseThrow().resourceId());
        stagedRecord.set(SongUpload.start(reserved, "Track One", albumId,
                new ArtistId(UUID.randomUUID()), actorId, "audio/mpeg", 1_000_000L,
                "crashed-mpu", T0.minusSeconds(3600), T0.plusSeconds(86400)));

        InitiateUploadIdempotentResult result =
                service.initiateUploadIdempotently(key, command("Track One"));

        assertFalse(result.replayed(), "recovery executes once more to reach completion");
        assertSame(stagedRecord.get(), result.upload());
        assertEquals(stagedRecord.get().getStagingKey(), result.presigned().storageKey(),
                "recovered URL must target the crashed attempt's staging key");
        verify(songUploadRepository, never()).insertIfAbsent(any());
        assertEquals(IdempotencyState.COMPLETED,
                idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow().state());
    }

    @Test
    void initiateUploadIdempotently_insertRaceLoser_abortsCreatedMultipartAndReusesWinner() {
        String key = validKey();
        // Model the real race: a crashed twin of this same logical operation (same key -> same
        // reserved song id) already inserted the staging record; our conditional insert loses.
        AtomicReference<SongId> reserved = new AtomicReference<>();
        doAnswer(inv -> {
            final String targetKey = inv.getArgument(0);
            if (reserved.get() == null) {
                reserved.set(SongId.from(targetKey.substring("pending/".length())));
                stagedRecord.set(SongUpload.start(reserved.get(), "Track One", albumId,
                        new ArtistId(UUID.randomUUID()), actorId, "audio/mpeg", 1_000_000L,
                        "twin-mpu", T0.minusSeconds(3600), T0.plusSeconds(86400)));
                return new PresignedUploadResult("pending/race-key", "upload-id",
                        T0.plus(Duration.ofMinutes(10)), true,
                        List.of(new PresignedUploadPart(1, "https://presigned/part1")));
            }
            return singlePartResult(targetKey);
        }).when(songStoragePort).regenerateUploadUrl(anyString(), any());
        doReturn(false).when(songUploadRepository).insertIfAbsent(any());

        InitiateUploadIdempotentResult result =
                service.initiateUploadIdempotently(key, command("Track One"));

        verify(songStoragePort).abortUpload("pending/race-key", "upload-id");
        assertEquals(reserved.get(), result.upload().getSongId(),
                "the loser must converge on the already-reserved song identity");
        assertSame(stagedRecord.get(), result.upload(),
                "the loser must reuse the winner's record, not its own");
        assertEquals(stagedRecord.get().getStagingKey(), result.presigned().storageKey());
        assertEquals(IdempotencyState.COMPLETED,
                idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow().state());
    }

    private static String validKey() {
        return "upload-it-" + UUID.randomUUID();
    }

    private IdempotencyScope scopeOf(final String rawKey) {
        return new IdempotencyScope(InitiateSongUploadIdempotentService.API_VERSION,
                "user:" + actorId, "POST", InitiateSongUploadIdempotentService.ROUTE_TEMPLATE,
                albumId.value().toString(), IdempotencyKey.of(rawKey));
    }

    private CanonicalRequestHash requestHash(final String title) {
        return CanonicalRequestHash.current(List.of(title, albumId.value().toString(),
                "audio/mpeg", String.valueOf(1_000_000L)));
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
