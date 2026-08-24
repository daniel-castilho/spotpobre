package com.spotpobre.backend.application.album.service;

import com.spotpobre.backend.application.album.port.in.CreateAlbumIdempotentlyUseCase;
import com.spotpobre.backend.application.album.port.in.CreateAlbumIdempotentlyUseCase.CreateAlbumCommand;
import com.spotpobre.backend.application.artist.port.in.RequireArtistAccessUseCase;
import com.spotpobre.backend.application.idempotency.Claim;
import com.spotpobre.backend.application.idempotency.IdempotencyCoordinator;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.ConflictException;
import com.spotpobre.backend.domain.common.Normalization;
import com.spotpobre.backend.domain.common.IdempotencyConflictException;
import com.spotpobre.backend.domain.common.IdempotencyInProgressException;
import com.spotpobre.backend.domain.common.IdempotencyKey;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.FailureDescriptor;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyScope;
import com.spotpobre.backend.domain.idempotency.model.ResultSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Idempotent album creation (spec §4.3, §5.4–§5.7, step 6C). Artist existence and the
 * membership/admin authorization run <b>before</b> the claim on every call — deterministic
 * failures never consume the key and replays are only served after re-authorization. The
 * reserved {@code AlbumId} is stable across crash recovery.
 */
@RequiredArgsConstructor
public class CreateAlbumIdempotentService implements CreateAlbumIdempotentlyUseCase {

    static final String API_VERSION = "v1";
    static final String ROUTE_TEMPLATE = "/api/v1/albums";
    static final Duration RETRY_AFTER_CAP = Duration.ofSeconds(30);

    private final IdempotencyCoordinator coordinator;
    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final RequireArtistAccessUseCase requireArtistAccess;
    private final Clock clock;

    @Override
    @Transactional
    public CreateAlbumOutcome createAlbumIdempotently(final String rawIdempotencyKey,
                                                      final CreateAlbumCommand command) {
        if (command == null || command.actorUserId() == null) {
            throw new IllegalArgumentException("Authenticated actor is required.");
        }
        if (command.artistId() == null) {
            throw new IllegalArgumentException("artistId is required");
        }
        final IdempotencyKey key = IdempotencyKey.of(rawIdempotencyKey);

        // Deterministic request validation AND authorization re-check happen before the claim:
        // these failures must not consume the key, and replays must not bypass the policy
        // (spec §5.4 ordering, §5.7 replay authorization).
        if (artistRepository.findById(command.artistId()).isEmpty()) {
            throw new NotFoundException("Artist not found: " + command.artistId());
        }
        requireArtistAccess.requireAccess(
                new RequireArtistAccessUseCase.ActorArtistRef(
                        command.actorUserId(), command.actorIsAdmin()),
                command.artistId());

        final IdempotencyScope scope = new IdempotencyScope(
                API_VERSION, "user:" + command.actorUserId(), "POST", ROUTE_TEMPLATE, "", key);
        final String name = Normalization.trim(command.name());
        final String coverArtUrl = Normalization.trim(command.coverArtUrl());
        final CanonicalRequestHash requestHash = CanonicalRequestHash.current(List.of(
                name,
                command.artistId().value().toString(),
                coverArtUrl == null ? "" : coverArtUrl));

        final var outcome = coordinator.claim(scope, requestHash, "CreateAlbum",
                IdempotencyResourceType.ALBUM, IdempotencyCoordinator.DEFAULT_CREATION_LEASE);

        if (outcome.replay().isPresent()) {
            return new CreateAlbumOutcome(loadReservedAlbum(outcome.replay().get().resourceId()), true);
        }
        if (outcome.replayedFailure().isPresent()) {
            throw failureToException(outcome.replayedFailure().get().failure());
        }
        if (outcome.isActiveLeaseElsewhere()) {
            long retryAfter = Math.min(RETRY_AFTER_CAP.getSeconds(),
                    coordinator.retryAfterSecondsFor(outcome.activeLease().orElse(null),
                            Duration.ofSeconds(2)));
            throw new IdempotencyInProgressException(
                    "An album creation with this Idempotency-Key is already in progress.", retryAfter);
        }
        if (outcome.isKeyReusedWithDifferentRequest()) {
            throw new IdempotencyConflictException(
                    "This Idempotency-Key was already used with a different request.");
        }

        final Claim claim = outcome.claimed().orElseThrow();
        try {
            final Album album = executeCreation(claim.resourceId(), command, name, coverArtUrl);
            coordinator.completeClaim(claim,
                    ResultSnapshot.jsonBody("{\"albumId\":\"" + claim.resourceId() + "\"}"),
                    clock.instant());
            return new CreateAlbumOutcome(album, false);
        } catch (ConflictException e) {
            coordinator.failClaim(claim, FailureDescriptor.of(409, "ALBUM_CONFLICT", safe(e.getMessage())),
                    clock.instant());
            throw e;
        }
        // Unexpected failures retain IN_PROGRESS for takeover-based recovery: the write may
        // have landed.
    }

    /** Crash recovery mirrors artist creation: a reserved album that already exists wins. */
    private Album executeCreation(final String reservedResourceId, final CreateAlbumCommand command,
                final String name, final String coverArtUrl) {
        final AlbumId reservedId = AlbumId.from(reservedResourceId);
        final Optional<Album> recovered = albumRepository.findById(reservedId);
        if (recovered.isPresent()) {
            return recovered.get();
        }

        final Album album = Album.builder()
                .id(reservedId)
                .name(name)
                .artistId(command.artistId())
                .coverArtUrl(coverArtUrl)
                .build();
        albumRepository.save(album);
        return album;
    }

    private Album loadReservedAlbum(final String albumId) {
        return albumRepository.findById(AlbumId.from(albumId))
                .orElseThrow(() -> new IdempotencyConflictException(
                        "The created album for this Idempotency-Key no longer exists."));
    }

    private RuntimeException failureToException(final FailureDescriptor failure) {
        if (failure.status() == 409) {
            return new ConflictException(failure.message());
        }
        return new IllegalArgumentException(failure.message());
    }

    private static String safe(final String message) {
        return message == null ? "Album creation conflict" : message;
    }
}
