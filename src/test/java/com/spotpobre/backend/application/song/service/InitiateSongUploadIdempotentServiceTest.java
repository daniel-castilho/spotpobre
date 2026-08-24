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
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.common.IdempotencyConflictException;
import com.spotpobre.backend.domain.common.IdempotencyInProgressException;
import com.spotpobre.backend.domain.common.IdempotencyKey;
import com.spotpobre.backend.domain.common.IdempotencyLeaseLostException;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyScope;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyState;
import com.spotpobre.backend.domain.song.model.PresignedUploadPart;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
    private SongMetadataRepository songMetadataRepository;
    private AlbumRepository albumRepository;
    private RequireArtistAccessUseCase requireArtistAccess;
    private InitiateSongUploadIdempotentService service;

    private final AlbumId albumId = new AlbumId(UUID.randomUUID());
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        idempotencyStore = new InMemoryIdempotencyRecordRepository();
        coordinator = new IdempotencyCoordinator(idempotencyStore, clock, NoopMetrics.INSTANCE);
        songStoragePort = mock(SongStoragePort.class);
        songMetadataRepository = mock(SongMetadataRepository.class);
        albumRepository = mock(AlbumRepository.class);
        requireArtistAccess = mock(RequireArtistAccessUseCase.class);
        service = new InitiateSongUploadIdempotentService(coordinator, songStoragePort,
                songMetadataRepository, albumRepository, requireArtistAccess, clock);

        Album album = Album.builder().id(albumId)
                .name("Album").artistId(new ArtistId(UUID.randomUUID())).build();
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(album));
        when(songMetadataRepository.findById(any())).thenReturn(Optional.empty());
        when(songStoragePort.generateUploadUrl(any())).thenAnswer(inv -> singlePartResult("fresh-key"));
        when(songStoragePort.regenerateUploadUrl(anyString(), any()))
                .thenAnswer((inv) -> singlePartResult(inv.getArgument(0)));
    }

    private static PresignedUploadResult singlePartResult(final String storageKey) {
        return new PresignedUploadResult(storageKey, null,
                Instant.parse("2026-03-02T09:10:00Z"), false,
                List.of(new PresignedUploadPart(1, "https://presigned/" + storageKey)));
    }

    private InitiateSongUploadCommand command(final String title) {
        return new InitiateSongUploadCommand(title, albumId, "audio/mpeg", 1_000_000L,
                actorId, false);
    }

    @Test
    void initiateUploadIdempotently_newKey_createsSongUnderReservedIdWithFreshKey() {
        String key = validKey();
        AtomicReference<Song> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(songMetadataRepository).save(any());

        InitiateUploadIdempotentResult result =
                service.initiateUploadIdempotently(key, command("Track One"));

        assertFalse(result.replayed());
        assertEquals(result.song().getStorageId(), result.upload().storageKey(),
                "presigned upload must target the song's storage key");

        var stored = idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow();
        assertEquals(IdempotencyState.COMPLETED, stored.state());
        assertEquals(result.song().getId().value().toString(), stored.resourceId());
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
        verify(songMetadataRepository, never()).save(any());
    }

    @Test
    void initiateUploadIdempotently_nonMemberActor_forbiddenBeforeClaim() {
        doThrow(new ForbiddenException("No membership"))
                .when(requireArtistAccess).requireAccess(any(), any());

        assertThrows(ForbiddenException.class,
                () -> service.initiateUploadIdempotently(validKey(), command("T")));
        verify(songMetadataRepository, never()).save(any());
    }

    @Test
    void initiateUploadIdempotently_sameKeySameRequest_replaysStoredResultWithFreshUrl() {
        String key = validKey();
        AtomicReference<Song> saved = new AtomicReference<>();
        when(songMetadataRepository.findById(any(SongId.class))).thenAnswer(inv -> {
            SongId asked = inv.getArgument(0);
            Song stored = saved.get();
            return stored != null && stored.getId().equals(asked)
                    ? Optional.of(stored) : Optional.empty();
        });
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(songMetadataRepository).save(any());

        InitiateUploadIdempotentResult first =
                service.initiateUploadIdempotently(key, command("Track One"));
        InitiateUploadIdempotentResult second =
                service.initiateUploadIdempotently(key, command("Track One"));

        assertFalse(first.replayed());
        assertTrue(second.replayed());
        assertEquals(first.song().getId(), second.song().getId());
        assertEquals(second.song().getStorageId(), second.upload().storageKey(),
                "replay must re-presign for the same storage key");
        verify(songStoragePort, times(1)).generateUploadUrl(any());
        verify(songStoragePort, times(1)).regenerateUploadUrl(anyString(), any());
    }

    @Test
    void initiateUploadIdempotently_sameKeyDifferentRequest_returnsKeyReuseConflict() {
        String key = validKey();
        doAnswer(inv -> null).when(songMetadataRepository).save(any());

        service.initiateUploadIdempotently(key, command("Track One"));

        assertThrows(IdempotencyConflictException.class,
                () -> service.initiateUploadIdempotently(key, command("Different Track")));
    }

    @Test
    void initiateUploadIdempotently_activeForeignLease_throwsInProgressWithCappedRetryAfter() {
        String key = validKey();
        coordinator.claim(scopeOf(key), requestHash("Track One"), "InitiateSongUpload",
                com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType.SONG_UPLOAD,
                IdempotencyCoordinator.UPLOAD_LEASE);

        InitiateSongUploadCommand cmd = command("Track One");
        // The direct probe above used a hash built from the same inputs; align it.
        var record = idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow();

        InitiateSongUploadCommand finalCmd = cmd;
        IdempotencyInProgressException exception = assertThrows(IdempotencyInProgressException.class,
                () -> service.initiateUploadIdempotently(key, finalCmd));

        assertTrue(exception.getRetryAfterSeconds() >= 1 && exception.getRetryAfterSeconds() <= 30);
        assertEquals(com.spotpobre.backend.domain.idempotency.model.IdempotencyState.IN_PROGRESS,
                record.state());
    }

    @Test
    void initiateUploadIdempotently_crashAfterSave_recoversExistingSongAndRegeneratesUrl() {
        String key = validKey();
        ClaimOutcome crashed = coordinator.claim(scopeOf(key), requestHash("Track One"),
                "InitiateSongUpload",
                com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType.SONG_UPLOAD,
                IdempotencyCoordinator.UPLOAD_LEASE);
        clock.advance(IdempotencyCoordinator.UPLOAD_LEASE.multipliedBy(2));

        Song writtenBeforeCrash = Song.create(
                SongId.from(crashed.claimed().orElseThrow().resourceId()),
                "Track One", albumId, "crashed-key");
        when(songMetadataRepository.findById(
                SongId.from(crashed.claimed().orElseThrow().resourceId())))
                .thenReturn(Optional.of(writtenBeforeCrash));

        InitiateUploadIdempotentResult result =
                service.initiateUploadIdempotently(key, command("Track One"));

        assertFalse(result.replayed(), "recovery executes once more to reach completion");
        assertSame(writtenBeforeCrash, result.song());
        assertEquals("crashed-key", result.upload().storageKey(),
                "recovered URL must target the crashed attempt's storage key");
        verify(songStoragePort, never()).generateUploadUrl(any());
        verify(songMetadataRepository, never()).save(any());
        assertEquals(IdempotencyState.COMPLETED,
                idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow().state());
    }

    @Test
    void initiateUploadIdempotently_metadataSaveFails_abortsMultipartAndRetainsInProgress() {
        String key = validKey();
        PresignedUploadResult multipart = new PresignedUploadResult("mp-key", "upload-id",
                Instant.parse("2026-03-02T09:10:00Z"), true,
                List.of(new PresignedUploadPart(1, "https://presigned/part1")));
        when(songStoragePort.generateUploadUrl(any())).thenReturn(multipart);
        doThrow(new IllegalStateException("DynamoDB down")).when(songMetadataRepository).save(any());

        assertThrows(IllegalStateException.class,
                () -> service.initiateUploadIdempotently(key, command("Track One")));

        verify(songStoragePort).abortUpload("mp-key", "upload-id");
        assertEquals(IdempotencyState.IN_PROGRESS,
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
