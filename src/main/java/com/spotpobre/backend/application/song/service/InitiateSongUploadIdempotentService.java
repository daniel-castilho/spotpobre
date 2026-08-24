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
import com.spotpobre.backend.domain.common.IdempotencyLeaseLostException;
import com.spotpobre.backend.domain.common.IdempotencyKey;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.FailureDescriptor;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyScope;
import com.spotpobre.backend.domain.idempotency.model.ResultSnapshot;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.model.SongUpload;
import com.spotpobre.backend.domain.song.model.SongUploadCommand;
import com.spotpobre.backend.domain.song.port.SongUploadRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import lombok.RequiredArgsConstructor;
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
 * fresh URL for the staging key already bound to the staged upload. No visible Song row exists
 * until confirmation promotes the upload (spec §7): pending uploads are invisible to
 * fetch/search/stream/like/playlist flows by construction.
 */
@RequiredArgsConstructor
public class InitiateSongUploadIdempotentService implements InitiateSongUploadIdempotentlyUseCase {

    static final String API_VERSION = "v1";
    static final String ROUTE_TEMPLATE = "/api/v1/albums/{albumId}/songs";
    static final Duration RETRY_AFTER_CAP = Duration.ofSeconds(30);
    /** Logical lifetime of a staged upload before cleanup reconciliation may abort it. */
    static final Duration STAGING_EXPIRY = Duration.ofHours(24);

    private final IdempotencyCoordinator coordinator;
    private final SongStoragePort songStoragePort;
    private final SongUploadRepository songUploadRepository;
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
            final SongUpload replayedRecord = songUploadRepository
                    .findBySongId(SongId.from(outcome.replay().get().resourceId()))
                    .orElseThrow(() -> new IdempotencyConflictException(
                            "The initiated upload for this Idempotency-Key no longer exists."));
            final PresignedUploadResult replayedUpload = presignFor(replayedRecord, command);
            return new InitiateUploadIdempotentResult(replayedRecord, replayedUpload, true);
        }
        return executeOrRecover(outcome, command, album);
    }

    private InitiateUploadIdempotentResult executeOrRecover(final ClaimOutcome outcome,
                                                            final InitiateSongUploadCommand command,
                                                            final Album album) {
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

            // Staging-only initiation (spec section 7): the reserved SongId is recorded in a
            // durable upload record; the Songs table is untouched until confirmation. Crash
            // recovery reuses the existing record and re-presigns its exact staging key.
            final Optional<SongUpload> recovered =
                    songUploadRepository.findBySongId(SongId.from(claim.resourceId()));
            final SongUpload staged;
            PresignedUploadResult upload;
            if (recovered.isPresent()) {
                staged = recovered.get();
                upload = presignFor(staged, command);
            } else {
                final String stagingKey = SongUpload.stagingKeyFor(SongId.from(claim.resourceId()));
                upload = songStoragePort.regenerateUploadUrl(stagingKey, uploadCommand);
                final SongUpload record = SongUpload.start(SongId.from(claim.resourceId()),
                        command.title(), command.albumId(), album.getArtistId(),
                        command.actorUserId(), command.contentType(), command.contentLengthBytes(),
                        upload.multipartUploadId(), clock.instant(),
                        clock.instant().plus(STAGING_EXPIRY));
                if (!songUploadRepository.insertIfAbsent(record)) {
                    // Lost an insert race: discard the multipart we may have created and reuse
                    // the winner's record so exactly one logical upload exists per key/song.
                    if (upload.multipartUploadId() != null) {
                        songStoragePort.abortUpload(upload.storageKey(), upload.multipartUploadId());
                    }
                    staged = songUploadRepository.findBySongId(SongId.from(claim.resourceId()))
                            .orElseThrow();
                    upload = presignFor(staged, command);
                } else {
                    staged = record;
                }
            }

            // Publish gate (spec §5.6): never return success for a result we could not record.
            if (!coordinator.completeClaim(claim,
                    ResultSnapshot.jsonBody("{\"songId\":\"" + claim.resourceId() + "\"}"),
                    clock.instant())) {
                throw new IdempotencyLeaseLostException(
                        "The idempotency lease was lost before the result could be recorded; "
                                + "retry with the same Idempotency-Key.");
            }
            return new InitiateUploadIdempotentResult(staged, upload, false);
        } catch (ConflictException e) {
            coordinator.failClaim(claim, FailureDescriptor.of(409, "SONG_CONFLICT", safe(e.getMessage())),
                    clock.instant());
            throw e;
        }
        // Unexpected failures retain IN_PROGRESS for takeover-based recovery: the metadata write
        // may have landed.
    }

    /**
     * Re-presigns a fresh URL against the record's bound staging key (replay/expiry recovery).
     * The multipart id is recovered from storage — never recreated — so duplicate S3 multipart
     * attempts cannot accumulate for one logical upload.
     */
    private PresignedUploadResult presignFor(final SongUpload record, final InitiateSongUploadCommand command) {
        return songStoragePort.regenerateUploadUrl(record.getStagingKey(),
                new SongUploadCommand(command.contentType(), command.contentLengthBytes()));
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
