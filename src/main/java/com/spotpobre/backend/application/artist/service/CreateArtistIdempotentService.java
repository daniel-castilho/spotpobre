package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.CreateArtistIdempotentlyUseCase;
import com.spotpobre.backend.application.artist.port.in.CreateArtistIdempotentlyUseCase.CreateArtistCommand;
import com.spotpobre.backend.application.idempotency.Claim;
import com.spotpobre.backend.application.idempotency.ClaimOutcome;
import com.spotpobre.backend.application.idempotency.IdempotencyCoordinator;
import com.spotpobre.backend.application.user.service.RegisterUserIdempotentService;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistAccount;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.ConflictException;
import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.common.IdempotencyConflictException;
import com.spotpobre.backend.domain.common.IdempotencyInProgressException;
import com.spotpobre.backend.domain.common.IdempotencyKey;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.FailureDescriptor;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyScope;
import com.spotpobre.backend.domain.idempotency.model.ResultSnapshot;
import com.spotpobre.backend.domain.user.model.Role;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Idempotent admin-only artist creation (spec §4.3, §5.4–§5.7, step 6B). The authenticated
 * admin's immutable user id scopes the claim; owner existence and {@code ROLE_ARTIST} are
 * validated <b>before</b> the claim so deterministic request failures never consume the key.
 * The reserved {@code ArtistId} is stable across crash recovery, and the atomic Artist + OWNER
 * transaction means recovery either finds the whole aggregate or re-creates it.
 */
@RequiredArgsConstructor
public class CreateArtistIdempotentService implements CreateArtistIdempotentlyUseCase {

    static final String API_VERSION = "v1";
    static final String ROUTE_TEMPLATE = "/api/v1/artists";
    static final Duration RETRY_AFTER_CAP = Duration.ofSeconds(30);

    private final IdempotencyCoordinator coordinator;
    private final ArtistRepository artistRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Override
    @Transactional
    public CreateArtistOutcome createArtistIdempotently(final String rawIdempotencyKey,
                                                        final UserId actorUserId,
                                                        final CreateArtistCommand command) {
        if (actorUserId == null) {
            throw new IllegalArgumentException("Authenticated actor is required.");
        }
        if (command == null || command.ownerUserId() == null) {
            throw new IllegalArgumentException("ownerUserId is required");
        }
        final IdempotencyKey key = IdempotencyKey.of(rawIdempotencyKey);

        // Deterministic request validation happens before the claim: these failures must not
        // consume the key (spec §5.4 ordering).
        validateOwner(command.ownerUserId());

        final IdempotencyScope scope = new IdempotencyScope(
                API_VERSION, "user:" + actorUserId.value(), "POST", ROUTE_TEMPLATE, "", key);
        final CanonicalRequestHash requestHash = CanonicalRequestHash.current(List.of(
                command.name(), String.valueOf(command.ownerUserId())));

        final ClaimOutcome outcome = coordinator.claim(scope, requestHash, "CreateArtist",
                IdempotencyResourceType.ARTIST, IdempotencyCoordinator.DEFAULT_CREATION_LEASE);

        if (outcome.replay().isPresent()) {
            return new CreateArtistOutcome(loadReservedArtist(outcome.replay().get().resourceId()), true);
        }
        if (outcome.replayedFailure().isPresent()) {
            throw failureToException(outcome.replayedFailure().get().failure());
        }
        if (outcome.isActiveLeaseElsewhere()) {
            long retryAfter = Math.min(RETRY_AFTER_CAP.getSeconds(),
                    coordinator.retryAfterSecondsFor(outcome.activeLease().orElse(null),
                            Duration.ofSeconds(2)));
            throw new IdempotencyInProgressException(
                    "An artist creation with this Idempotency-Key is already in progress.", retryAfter);
        }
        if (outcome.isKeyReusedWithDifferentRequest()) {
            throw new IdempotencyConflictException(
                    "This Idempotency-Key was already used with a different request.");
        }

        final Claim claim = outcome.claimed().orElseThrow();
        try {
            final Artist artist = executeCreation(claim.resourceId(), command);
            coordinator.completeClaim(claim,
                    ResultSnapshot.jsonBody("{\"artistId\":\"" + claim.resourceId() + "\"}"),
                    clock.instant());
            return new CreateArtistOutcome(artist, false);
        } catch (ConflictException e) {
            coordinator.failClaim(claim, FailureDescriptor.of(409, "ARTIST_CONFLICT", safe(e.getMessage())),
                    clock.instant());
            throw e;
        }
        // Unexpected failures retain IN_PROGRESS for takeover-based recovery (no release):
        // the atomic write may have landed.
    }

    /** Crash recovery mirrors registration: a reserved artist that already exists wins. */
    private Artist executeCreation(final String reservedResourceId, final CreateArtistCommand command) {
        final ArtistId reservedId = ArtistId.from(reservedResourceId);
        final Optional<Artist> recovered = artistRepository.findById(reservedId);
        if (recovered.isPresent()) {
            return recovered.get();
        }

        final Artist artist = Artist.create(reservedId, command.name());
        final ArtistAccount ownerAccount =
                ArtistAccount.owner(artist.getId(), command.ownerUserId(), clock.instant());
        artistRepository.createWithOwner(artist, ownerAccount);
        return artist;
    }

    private void validateOwner(final java.util.UUID ownerUserId) {
        final User owner = userRepository.findById(new UserId(ownerUserId))
                .orElseThrow(() -> new NotFoundException(
                        "Owner user not found: " + ownerUserId));
        if (!owner.getRoles().contains(Role.ARTIST)) {
            throw new ForbiddenException(
                    "Owner user must have ROLE_ARTIST: " + ownerUserId);
        }
    }

    private Artist loadReservedArtist(final String artistId) {
        return artistRepository.findById(ArtistId.from(artistId))
                .orElseThrow(() -> new IdempotencyConflictException(
                        "The created artist for this Idempotency-Key no longer exists."));
    }

    private RuntimeException failureToException(final FailureDescriptor failure) {
        if (failure.status() == 409) {
            return new ConflictException(failure.message());
        }
        return new IllegalArgumentException(failure.message());
    }

    private static String safe(final String message) {
        return message == null ? "Artist creation conflict" : message;
    }
}
