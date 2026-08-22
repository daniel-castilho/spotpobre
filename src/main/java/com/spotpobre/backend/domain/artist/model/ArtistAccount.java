package com.spotpobre.backend.domain.artist.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Membership of a user on an artist resource. A user holds at most one account per artist;
 * {@code ROLE_ARTIST} alone never grants management rights — this aggregate does.
 *
 * <p>Pure domain type: no framework annotations.</p>
 */
public record ArtistAccount(
        ArtistId artistId,
        UUID userId,
        ArtistPermission permission,
        Instant createdAt
) {

    public ArtistAccount {
        Objects.requireNonNull(artistId, "artistId is required");
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(permission, "permission is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static ArtistAccount owner(final ArtistId artistId, final UUID userId, final Instant createdAt) {
        return new ArtistAccount(artistId, userId, ArtistPermission.OWNER, createdAt);
    }

    public static ArtistAccount manager(final ArtistId artistId, final UUID userId, final Instant createdAt) {
        return new ArtistAccount(artistId, userId, ArtistPermission.MANAGER, createdAt);
    }
}
