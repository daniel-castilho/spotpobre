package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.idempotency.Claim;
import com.spotpobre.backend.application.idempotency.IdempotencyCoordinator;
import com.spotpobre.backend.application.playlist.port.in.CreatePlaylistIdempotentlyUseCase;
import com.spotpobre.backend.domain.common.ConflictException;
import com.spotpobre.backend.domain.common.Normalization;
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
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotent playlist creation (spec §4.3, §5.4–§5.7, step 6D). The authenticated actor owns the
 * playlist and scopes the claim. User existence is validated before the claim (deterministic,
 * never consumes the key); the per-user playlist limit is state-dependent, so it is enforced at
 * execution time — exceeding it records a replayable FAILED_FINAL 409. The reserved
 * {@code PlaylistId} is stable across crash recovery.
 */
@RequiredArgsConstructor
public class CreatePlaylistIdempotentService implements CreatePlaylistIdempotentlyUseCase {

    static final String API_VERSION = "v1";
    static final String ROUTE_TEMPLATE = "/api/v1/playlists";
    static final Duration RETRY_AFTER_CAP = Duration.ofSeconds(30);

    private final IdempotencyCoordinator coordinator;
    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;
    private final Clock clock;

    @Override
    @Transactional
    public CreatePlaylistOutcome createPlaylistIdempotently(final String rawIdempotencyKey,
                                                            final UUID actorUserId,
                                                            final String name) {
        if (actorUserId == null) {
            throw new IllegalArgumentException("Authenticated actor is required.");
        }
        final String trimmedName = Normalization.trim(name);
        if (trimmedName == null || trimmedName.isBlank()) {
            throw new IllegalArgumentException("Playlist name cannot be blank");
        }
        final IdempotencyKey key = IdempotencyKey.of(rawIdempotencyKey);
        final UserId ownerId = new UserId(actorUserId);

        // Deterministic pre-claim validation: must not consume the key (spec §5.4 ordering).
        if (userRepository.findById(ownerId).isEmpty()) {
            throw new NotFoundException("User not found");
        }

        final IdempotencyScope scope = new IdempotencyScope(
                API_VERSION, "user:" + actorUserId, "POST", ROUTE_TEMPLATE, "", key);
        final CanonicalRequestHash requestHash = CanonicalRequestHash.current(List.of(trimmedName));

        final var outcome = coordinator.claim(scope, requestHash, "CreatePlaylist",
                IdempotencyResourceType.PLAYLIST, IdempotencyCoordinator.DEFAULT_CREATION_LEASE);

        if (outcome.replay().isPresent()) {
            return new CreatePlaylistOutcome(loadReservedPlaylist(outcome.replay().get().resourceId()), true);
        }
        if (outcome.replayedFailure().isPresent()) {
            // A FAILED_FINAL playlist creation can only mean the playlist-limit conflict recorded
            // at execution time; the key stays bound to that outcome until it expires.
            final FailureDescriptor failure = outcome.replayedFailure().get().failure();
            throw new ConflictException(failure.message());
        }
        if (outcome.isActiveLeaseElsewhere()) {
            long retryAfter = Math.min(RETRY_AFTER_CAP.getSeconds(),
                    coordinator.retryAfterSecondsFor(outcome.activeLease().orElse(null),
                            Duration.ofSeconds(2)));
            throw new IdempotencyInProgressException(
                    "A playlist creation with this Idempotency-Key is already in progress.", retryAfter);
        }
        if (outcome.isKeyReusedWithDifferentRequest()) {
            throw new IdempotencyConflictException(
                    "This Idempotency-Key was already used with a different request.");
        }

        final Claim claim = outcome.claimed().orElseThrow();
        try {
            final Playlist playlist = executeCreation(claim.resourceId(), ownerId, trimmedName);
            // Publish gate (spec §5.6): never return success for a result we could not record.
            if (!coordinator.completeClaim(claim,
                    ResultSnapshot.jsonBody("{\"playlistId\":\"" + claim.resourceId() + "\"}"),
                    clock.instant())) {
                throw new IdempotencyLeaseLostException(
                        "The idempotency lease was lost before the result could be recorded; "
                                + "retry with the same Idempotency-Key.");
            }
            return new CreatePlaylistOutcome(playlist, false);
        } catch (ConflictException e) {
            coordinator.failClaim(claim,
                    FailureDescriptor.of(409, "PLAYLIST_LIMIT_EXCEEDED", safe(e.getMessage())),
                    clock.instant());
            throw e;
        }
        // Unexpected failures retain IN_PROGRESS for takeover-based recovery (no release):
        // the write may have landed.
    }

    /** Crash recovery: a reserved playlist that already exists wins. */
    private Playlist executeCreation(final String reservedResourceId, final UserId ownerId,
                                     final String name) {
        final PlaylistId reservedId = PlaylistId.from(reservedResourceId);
        final Optional<Playlist> recovered = playlistRepository.findById(reservedId);
        if (recovered.isPresent()) {
            return recovered.get();
        }

        final Playlist playlist = Playlist.create(reservedId, name, ownerId);
        // Atomic at the storage layer: exceeding the per-owner limit aborts the whole
        // transaction and surfaces as a replayable FAILED_FINAL 409 via the catch above.
        playlistRepository.createWithinOwnerLimit(playlist, User.MAX_PLAYLISTS_PER_USER);
        return playlist;
    }

    private Playlist loadReservedPlaylist(final String playlistId) {
        return playlistRepository.findById(PlaylistId.from(playlistId))
                .orElseThrow(() -> new IdempotencyConflictException(
                        "The created playlist for this Idempotency-Key no longer exists."));
    }

    private static String safe(final String message) {
        return message == null ? "Playlist creation conflict" : message;
    }
}
