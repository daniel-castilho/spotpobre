package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.artist.port.in.RequireArtistAccessUseCase;
import com.spotpobre.backend.application.idempotency.Claim;
import com.spotpobre.backend.application.idempotency.ClaimOutcome;
import com.spotpobre.backend.application.idempotency.IdempotencyCoordinator;
import com.spotpobre.backend.application.song.port.in.InitiateSongUploadIdempotentlyUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.common.ConflictException;
import com.spotpobre.backend.domain.common.IdempotencyConflictException;
import com.spotpobre.backend.domain.common.IdempotencyInProgressException;
import com.spotpobre.backend.domain.common.IdempotencyKey;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.FailureDescriptor;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyScope;
import com.spotpobre.backend.domain.idempotency.model.ResultSnapshot;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.model.SongUploadCommand;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Idempotent song upload initiation (spec §4.3, §5.4–§5.7, step 6E) using the long upload lease
 * (120 s). Album existence and membership authorization run before the claim on every call —
 * deterministic failures never consume the key and replays cannot bypass the policy. The
 * reserved {@code SongId} is stable across crash recovery; replays and recoveries re-presign a
 * fresh URL for the storage key already bound to the reserved song.
 */
@RequiredArgsConstructor
public class InitiateSongUploadIdempotentService implements InitiateSongUploadIdempotentlyUseCase {

    private static final Logger logger = LoggerFactory.getLogger(InitiateSongUploadIdempotentService.class);

    static final String API_VERSION = "v1";
    static final String ROUTE_TEMPLATE = "/api/v1/albums/{albumId}/songs";
    static final Duration RETRY_AFTER_CAP = Duration.ofSeconds(30);

    private final IdempotencyCoordinator coordinator;
    private final SongStoragePort songStoragePort;
    private final SongMetadataRepository songMetadataRepository;
    private final AlbumRepository albumRepository;
    private final RequireArtistAccessUseCase requireArtistAccess;
    private final Clock clock;

    @Override
    @Transactional
    public InitiateUploadIdempotentResult initiateUploadIdempotently(final String rawIdempotencyKey,
                                                                     final InitiateSongUploadCommand command) {
        if (command == null || command.actorUserId() == null || command.albumId() == null) {
            throw new IllegalArgumentException("Authenticated actor and albumId are required.");
        }
        final IdempotencyKey key = IdempotencyKey.of(rawIdempotencyKey);

        // Deterministic pre-claim validation AND authorization re-check: must not consume the
        // key, and replays must not bypass the policy (spec §5.4 ordering, §5.7).
        final Album album = albumRepository.findById(command.albumId())
                .orElseThrow(() -> new NotFoundException("Album not found: " + command.albumId()));
        requireArtistAccess.requireAccess(
                new RequireArtistAccessUseCase.ActorArtistRef(command.actorUserId(), command.actorIsAdmin()),
                album.getArtistId());

        final IdempotencyScope scope = new IdempotencyScope(
                API_VERSION, "user:" + command.actorUserId(), "POST", ROUTE_TEMPLATE,
                command.albumId().value().toString(), key);
        final CanonicalRequestHash requestHash = CanonicalRequestHash.current(List.of(
                command.title(),
                command.albumId().value().toString(),
                command.contentType() == null ? "" : command.contentType(),
                String.valueOf(command.contentLengthBytes())));

        final var outcome = coordinator.claim(scope, requestHash, "InitiateSongUpload",
                IdempotencyResourceType.SONG_UPLOAD, IdempotencyCoordinator.UPLOAD_LEASE);

        if (outcome.replay().isPresent()) {
            final Song replayedSong = songMetadataRepository
                    .findById(SongId.from(outcome.replay().get().resourceId()))
                    .orElseThrow(() -> new IdempotencyConflictException(
                            "The initiated song for this Idempotency-Key no longer exists."));
            final PresignedUploadResult replayedUpload = songStoragePort.regenerateUploadUrl(
                    replayedSong.getStorageId(),
                    new SongUploadCommand(command.contentType(), command.contentLengthBytes()));
            return new InitiateUploadIdempotentResult(replayedSong, replayedUpload, true);
        }
        return executeOrRecover(outcome, command);
    }

    private InitiateUploadIdempotentResult executeOrRecover(final ClaimOutcome outcome,
                                                            final InitiateSongUploadCommand command) {
        if (outcome.replayedFailure().isPresent()) {
            throw failureToException(outcome.replayedFailure().get().failure());
        }
        if (outcome.isActiveLeaseElsewhere()) {
            long retryAfter = Math.min(RETRY_AFTER_CAP.getSeconds(),
                    coordinator.retryAfterSecondsFor(outcome.activeLease().orElse(null),
                            Duration.ofSeconds(2)));
            throw new IdempotencyInProgressException(
                    "A song upload initiation with this Idempotency-Key is already in progress.",
                    retryAfter);
        }
        if (outcome.isKeyReusedWithDifferentRequest()) {
            throw new IdempotencyConflictException(
                    "This Idempotency-Key was already used with a different request.");
        }

        final Claim claim = outcome.claimed().orElseThrow();
        try {
            final SongUploadCommand uploadCommand = new SongUploadCommand(
                    command.contentType(), command.contentLengthBytes());

            // Crash recovery: metadata under the reserved ID may already exist from a crashed
            // attempt — reuse it and hand out a fresh presigned URL for its storage key.
            final Optional<Song> recovered =
                    songMetadataRepository.findById(SongId.from(claim.resourceId()));
            final Song song;
            final PresignedUploadResult upload;
            if (recovered.isPresent()) {
                song = recovered.get();
                upload = songStoragePort.regenerateUploadUrl(song.getStorageId(), uploadCommand);
            } else {
                upload = songStoragePort.generateUploadUrl(uploadCommand);
                song = Song.create(SongId.from(claim.resourceId()),
                        command.title(), command.albumId(), upload.storageKey());
                try {
                    songMetadataRepository.save(song);
                } catch (RuntimeException e) {
                    if (upload.multipartUploadId() != null) {
                        songStoragePort.abortUpload(upload.storageKey(), upload.multipartUploadId());
                    } else {
                        logger.warn("Metadata save failed after generating presigned upload for key {}; " +
                                "no object was created yet, nothing to abort.", upload.storageKey());
                    }
                    throw e;
                }
            }

            coordinator.completeClaim(claim,
                    ResultSnapshot.jsonBody("{\"songId\":\"" + claim.resourceId() + "\"}"),
                    clock.instant());
            return new InitiateUploadIdempotentResult(song, upload, false);
        } catch (ConflictException e) {
            coordinator.failClaim(claim, FailureDescriptor.of(409, "SONG_CONFLICT", safe(e.getMessage())),
                    clock.instant());
            throw e;
        }
        // Unexpected failures retain IN_PROGRESS for takeover-based recovery: the metadata write
        // may have landed.
    }

    private RuntimeException failureToException(final FailureDescriptor failure) {
        if (failure.status() == 409) {
            return new ConflictException(failure.message());
        }
        return new IllegalArgumentException(failure.message());
    }

    private static String safe(final String message) {
        return message == null ? "Song initiation conflict" : message;
    }
}
